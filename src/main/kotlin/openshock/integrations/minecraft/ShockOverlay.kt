package openshock.integrations.minecraft

import net.minecraft.client.Minecraft
import openshock.integrations.minecraft.config.ShockCraftConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// The HUD's drawing surface. GuiGraphics was split out into GuiGraphicsExtractor in 26.1, when the
// HUD stopped drawing as it walked and started recording a render state instead - but the handful
// of calls used here (fill, guiWidth, guiHeight) are spelled identically on both, so an alias is
// the whole of the difference. See HudMixin for the other half of it.
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor as Graphics
//?} else {
/*import net.minecraft.client.gui.GuiGraphics as Graphics
*///?}

/**
 * What *you* see while your own shocker is running: lightning crawling around the edge of the
 * screen for as long as the control lasts.
 *
 * The counterpart to [ShockEffects], and the division between them is who it is for. That one runs
 * on the server and dresses you for the room - sparks and a crackle everyone nearby gets, which is
 * why it costs a packet and why the wearer has to opt into it. This one never leaves the machine
 * it is drawn on: nothing is sent, nothing is received, and nobody else can tell it is happening.
 * Which is also why it is the only part of the effect that is on by default.
 *
 * Fed from [openshock.integrations.minecraft.api.OpenShockApi.control], the single funnel every
 * control in the mod passes through, so the overlay appears for a shock however it was caused - a
 * mob, a fall, a level-up, a chat phrase or somebody's remote - and never for one the backend
 * turned down.
 *
 * Only shocks are drawn. A buzz and a beep are things you feel and hear rather than things that
 * want the screen, and dressing them the same way would mean a remote set to Sound could blind
 * you. [begin] is simply not called for them.
 *
 * ### Drawing
 *
 * Everything here is [Graphics.fill] and nothing else: no texture, no shader, no model. That is
 * deliberate, because `fill` is the one drawing call spelled the same way on every version this
 * mod builds for, where anything involving a texture changed shape three times between 1.20.4 and
 * 26.2. The cost of it is that a bolt has to be built out of rectangles, which is what [segment]
 * is about.
 */
object ShockOverlay {

    /** Long enough to be seen, short enough that a 300ms shock is not mostly fade. */
    private const val FADE_IN_MS = 80L

    /** The tail after the control ends, so the screen calms down rather than snapping clear. */
    private const val FADE_OUT_MS = 280L

    /** Longer than any control OpenShock will run, matching the guard in [ShockEffects]. */
    private const val MAX_DURATION_MS = 30_000L

    /**
     * How long one arrangement of bolts stays on screen before every one of them is rebuilt.
     *
     * This is the whole of the animation: the bolts do not move, they are replaced. Lightning that
     * slides across the screen reads as a ribbon; lightning that jumps to a new shape fourteen
     * times a second reads as lightning.
     */
    private const val STRIKE_MS = 70L

    /** The white-out at the moment a shock lands. Kept short - it is punctuation, not lighting. */
    private const val FLASH_MS = 140L

    /** Rings of the edge glow. More is smoother, and each one costs four rectangles. */
    private const val GLOW_BANDS = 16

    /** Nodes per bolt, minus one. Eight is enough kinks to read as jagged and no more. */
    private const val BOLT_SEGMENTS = 8

    /** At full intensity. One at the bottom of the range, so a nudge still shows something. */
    private const val MAX_BOLTS = 4

    /** How often a bolt branches, and how far the branch runs. */
    private const val FORK_CHANCE = 0.5f
    private const val FORK_SEGMENTS = 3

    /** Which side of the screen a bolt is anchored to. */
    private const val EDGE_TOP = 0
    private const val EDGE_RIGHT = 1
    private const val EDGE_BOTTOM = 2
    private const val EDGE_LEFT = 3

    /** Near-white blue for the core of a bolt; a deeper blue for everything around it. */
    private const val CORE = 0xD8F4FF
    private const val HALO = 0x3C7BFF

    private var startedAt = 0L
    private var endsAt = 0L

    /** Intensity as 0..1, which is the only thing deciding how big any of this gets. */
    private var strength = 0f

    /**
     * One bolt's nodes, in edge space - `u` along the edge, `v` inwards from it. Scratch rather
     * than allocated per bolt: [render] is the client thread and nothing else touches these, and a
     * bolt is rebuilt several times a second for as long as a shock runs.
     */
    private val us = FloatArray(BOLT_SEGMENTS + 1)
    private val vs = FloatArray(BOLT_SEGMENTS + 1)

    /**
     * A shock the backend has just accepted.
     *
     * A second one landing while the first is still running extends it and takes the stronger of
     * the two, rather than restarting the fade and flashing again - a run of presses should look
     * like one continuous shock, because that is what it feels like.
     *
     * Called from the coroutine that finished the HTTP call, so it hops onto the client thread for
     * the same reason [openshock.integrations.minecraft.platform.NetClient.sendShocked] does: the
     * fields below are read by the renderer and written nowhere else.
     */
    fun begin(intensity: Byte, durationMs: Int) {
        if (!ShockCraftConfig.HANDLER.instance().shockScreenOverlay) return

        val length = durationMs.toLong().coerceIn(0, MAX_DURATION_MS)
        if (length == 0L) return

        Minecraft.getInstance().execute {
            val now = System.currentTimeMillis()

            if (now > endsAt) {
                startedAt = now
                strength = 0f
            }

            endsAt = max(endsAt, now + length)
            strength = max(strength, intensity.toInt().coerceIn(0, 100) / 100f)
        }
    }

    /**
     * One frame, called from the tail of whatever draws the HUD on this version. See HudMixin.
     *
     * Vanilla only reaches that method when the HUD is being drawn at all, so F1 and everything
     * else that hides the HUD hides this with it and there is nothing to check for here.
     */
    fun render(graphics: Graphics) {
        val now = System.currentTimeMillis()
        if (now >= endsAt + FADE_OUT_MS) return

        val fade = fade(now)
        if (fade <= 0f) return

        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        if (width <= 0 || height <= 0) return

        // Vanilla's Distortion Effects slider, which is where somebody who does not want a screen
        // flashing at them has already said so - it is what the nausea and portal overlays are
        // damped by. Only the flash and the flicker depend on it: turned all the way down, the
        // lightning is steady rather than absent, so the overlay still says a shock is running.
        val distortion = Minecraft.getInstance().options.screenEffectScale().get().toFloat()

        // One seed per strike, so every bolt is rebuilt on the same frame and the whole screen
        // jumps at once. Seeding off the clock rather than carrying state also means the overlay
        // has nothing to keep between frames.
        val strike = (now - startedAt) / STRIKE_MS
        val rng = Random(strike)

        val flicker = 1f - 0.35f * distortion * rng.nextFloat()
        val alpha = fade * flicker

        glow(graphics, width, height, alpha)

        val sinceStart = now - startedAt
        if (sinceStart < FLASH_MS) {
            val left = 1f - sinceStart.toFloat() / FLASH_MS
            val punch = fade * left * left * 0.22f * strength * distortion
            graphics.fill(0, 0, width, height, argb(punch, CORE))
        }

        repeat(1 + (strength * (MAX_BOLTS - 1)).toInt()) {
            bolt(graphics, width, height, rng, alpha)
        }
    }

    /** In over [FADE_IN_MS], out over [FADE_OUT_MS], and whichever of the two is further along. */
    private fun fade(now: Long): Float {
        if (now < startedAt) return 0f

        val rising = (now - startedAt).toFloat() / FADE_IN_MS
        val remaining = endsAt - now
        val falling = if (remaining >= 0) 1f else 1f + remaining.toFloat() / FADE_OUT_MS

        return min(rising, falling).coerceIn(0f, 1f)
    }

    /**
     * The charged look around the frame: rings fading inwards, drawn as four bars each so the
     * corners are not painted twice and left twice as bright.
     */
    private fun glow(graphics: Graphics, width: Int, height: Int, alpha: Float) {
        val depth = (min(width, height) * (0.06f + 0.10f * strength)).toInt()
        val band = max(1, depth / GLOW_BANDS)

        // Reaching further in *and* burning brighter, so a light shock is a rim around the screen
        // and a hard one is the frame lit up.
        val lit = alpha * (0.45f + 0.55f * strength)

        for (ring in 0 until GLOW_BANDS) {
            val falloff = 1f - ring.toFloat() / GLOW_BANDS
            val colour = argb(lit * falloff * falloff * 0.5f, HALO)
            if (colour ushr 24 == 0) continue

            val out = ring * band
            val inner = out + band

            graphics.fill(out, out, width - out, inner, colour)
            graphics.fill(out, height - inner, width - out, height - out, colour)
            graphics.fill(out, inner, inner, height - inner, colour)
            graphics.fill(width - inner, inner, width - out, height - inner, colour)
        }
    }

    /**
     * One bolt: a jagged line anchored to a random edge, bowed away from it, with a branch off a
     * middle node half the time.
     *
     * Built in edge space so that all four sides are the same code - `u` runs along the edge, `v`
     * inwards from it - and only [projectX] and [projectY] know which side is which.
     *
     * Bolts hug the edge on purpose. Being shocked is not a good moment to have the middle of the
     * screen painted over, and how far in they reach is the clearest thing intensity has to say: a
     * light shock flickers around the frame, a hard one throws lightning well into the view.
     */
    private fun bolt(graphics: Graphics, width: Int, height: Int, rng: Random, alpha: Float) {
        val edge = rng.nextInt(4)
        val along = if (edge == EDGE_TOP || edge == EDGE_BOTTOM) width else height
        val reach = min(width, height) * (0.05f + 0.20f * strength)

        val span = along * (0.14f + 0.26f * rng.nextFloat())
        val start = rng.nextFloat() * (along - span)

        for (node in 0..BOLT_SEGMENTS) {
            val travelled = node.toFloat() / BOLT_SEGMENTS
            us[node] = start + span * travelled

            // Widest in the middle, so the bolt arcs off the edge and comes back to it rather than
            // ending in mid-air, and jittered per node so no two strikes are the same shape.
            vs[node] = reach * sin(travelled * PI).toFloat() * (0.3f + 0.7f * rng.nextFloat())
        }

        for (node in 1..BOLT_SEGMENTS) {
            segment(graphics, edge, width, height, us[node - 1], vs[node - 1], us[node], vs[node], alpha)
        }

        if (rng.nextFloat() >= FORK_CHANCE) return

        // Lightning is only legible as lightning when it branches; one wavy line reads as a
        // ribbon. The fork heads further inwards and is drawn dimmer, so it stays a branch rather
        // than becoming a second bolt.
        val from = 2 + rng.nextInt(BOLT_SEGMENTS - 3)
        var u = us[from]
        var v = vs[from]

        repeat(FORK_SEGMENTS) {
            val nextU = u + (rng.nextFloat() - 0.5f) * reach * 1.2f
            val nextV = v + reach * (0.25f + 0.35f * rng.nextFloat())

            segment(graphics, edge, width, height, u, v, nextU, nextV, alpha * 0.7f)

            u = nextU
            v = nextV
        }
    }

    /**
     * One straight piece of a bolt, as a bright core inside a dimmer halo.
     *
     * Drawn as runs along whichever axis the piece mostly travels: a near-horizontal segment costs
     * one rectangle per row it crosses instead of one per pixel of its length. With `fill` as the
     * only tool available that is the difference between a few hundred rectangles a frame and a
     * few thousand, and it only happens at all while a shock is running.
     */
    private fun segment(
        graphics: Graphics,
        edge: Int,
        width: Int,
        height: Int,
        fromU: Float,
        fromV: Float,
        toU: Float,
        toV: Float,
        alpha: Float,
    ) {
        val core = argb(alpha, CORE)
        if (core ushr 24 == 0) return
        val halo = argb(alpha * 0.4f, HALO)

        val x0 = projectX(edge, fromU, fromV, width)
        val y0 = projectY(edge, fromU, fromV, height)
        val x1 = projectX(edge, toU, toV, width)
        val y1 = projectY(edge, toU, toV, height)

        val dx = x1 - x0
        val dy = y1 - y0

        if (abs(dx) >= abs(dy)) {
            val rows = abs(dy)
            val steps = max(rows, 1)

            for (row in 0..rows) {
                val y = if (dy >= 0) y0 + row else y0 - row
                val a = x0 + dx * row / steps
                val b = if (row == rows) x1 else x0 + dx * (row + 1) / steps

                val left = min(a, b)
                val right = max(a, b) + 1

                graphics.fill(left - 1, y - 1, right + 1, y + 2, halo)
                graphics.fill(left, y, right, y + 1, core)
            }
        } else {
            val columns = abs(dx)
            val steps = max(columns, 1)

            for (column in 0..columns) {
                val x = if (dx >= 0) x0 + column else x0 - column
                val a = y0 + dy * column / steps
                val b = if (column == columns) y1 else y0 + dy * (column + 1) / steps

                val top = min(a, b)
                val bottom = max(a, b) + 1

                graphics.fill(x - 1, top - 1, x + 2, bottom + 1, halo)
                graphics.fill(x, top, x + 1, bottom, core)
            }
        }
    }

    /** Edge space to screen space. `v` is measured inwards, so the far sides count backwards. */
    private fun projectX(edge: Int, u: Float, v: Float, width: Int): Int = when (edge) {
        EDGE_RIGHT -> width - 1 - v.toInt()
        EDGE_LEFT -> v.toInt()
        else -> u.toInt()
    }

    private fun projectY(edge: Int, u: Float, v: Float, height: Int): Int = when (edge) {
        EDGE_BOTTOM -> height - 1 - v.toInt()
        EDGE_TOP -> v.toInt()
        else -> u.toInt()
    }

    /** An opacity and a colour, as the packed ARGB that every version of `fill` takes. */
    private fun argb(alpha: Float, rgb: Int): Int =
        ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or rgb
}
