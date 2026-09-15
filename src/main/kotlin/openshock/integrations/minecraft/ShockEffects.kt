package openshock.integrations.minecraft

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import openshock.integrations.minecraft.api.RemoteMode
import openshock.integrations.minecraft.platform.McCompat
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
//? if >=1.21.4 {
import openshock.integrations.minecraft.content.ModContent
//?} else {
/*import net.minecraft.sounds.SoundEvents
*///?}

/**
 * What the room sees and hears when something lands on somebody, played on the server so everyone
 * nearby gets it.
 *
 * Runs on the server side only and never touches a client class - a dedicated server loads this.
 * It is fed by [openshock.integrations.minecraft.platform.ShockedPayload], which arrives from the
 * client that just had a control accepted by the OpenShock API.
 *
 * All three modes are dressed differently, because a bystander should be able to tell a jolt from
 * a buzz from a beep: sparks and a crackle, tan flecks and a low buzz, notes and a chime. The
 * particles are vanilla; the sounds go through the mod's own events, so what they actually are is
 * decided by `assets/shockcraft/sounds.json` rather than by anything here.
 *
 * A control lasts a while, so this does too: one arriving packet registers a crackle that keeps
 * going until it runs out, rather than one puff at the moment it lands. That is the whole reason
 * there is a tick here at all.
 *
 * Nothing a client sends is trusted further than the size of the puff. Mode falls back to Shock,
 * intensity and duration are clamped on arrival, and a player gets exactly one crackle at a time -
 * a second packet replaces the first rather than stacking on it - so a client that spams the
 * channel ends up drawing the same effects around itself that one packet would have.
 *
 * Both ways in are the server thread - both loaders hand payloads to the main thread, and the tick
 * is the main thread by definition - so the map below needs no locking.
 */
object ShockEffects {

    /** Longer than any control OpenShock will run, so a silly number cannot leave sparks behind. */
    private const val MAX_DURATION_MS = 30_000

    /** The shortest crackle there can be, so the briefest control the backend takes still shows. */
    private const val MIN_TICKS = 2L

    /**
     * How often the arcs are thrown away and new ones struck: every other tick, ten times a
     * second.
     *
     * Fast, because that is what an arc does. These are not one bolt being held - each is an
     * independent flash jumping a gap near the shocker, so there is nothing to keep between
     * bursts and the flicker is the effect.
     */
    private const val BURST_TICKS = 2L

    /** The two ankles the shocker sits on, as a sign along the player's right. */
    private val ANKLES = intArrayOf(-1, 1)

    /**
     * Where the shocker is on each ankle: this far out along the player's right, as a fraction
     * of the body radius, and this high off the ground. Shared by every mode, so a jolt, a buzz
     * and a beep all come from the same place on the model.
     */
    private const val CUFF_OUT = 0.75
    private const val CUFF_HEIGHT = 0.1

    /** Arcs at once at zero intensity, and how many more a full-strength control adds. */
    private const val MIN_ARCS = 2
    private const val ARCS_PER_HUNDRED = 4

    /**
     * Pieces per arc. Two, so each one has a kink in it.
     *
     * A single straight streak reads as a needle; one bend is enough to read as an arc, and more
     * would only cost particles to draw the same small thing. The pieces of one arc are drawn
     * overlapping rather than exactly joined - they do not have to meet, and not requiring it is
     * what keeps this robust.
     */
    private const val ARC_PIECES = 2

    /** How long one piece is, in blocks. Short: an arc jumps a gap, it does not wind anywhere. */
    private const val PIECE_MIN = 0.07
    private const val PIECE_VARY = 0.09

    /**
     * How a mode arranges its particles.
     *
     * [CLOUD] is a puff at each shocker, which is all a buzz or a beep needs to be visible.
     * [ARCS] draws electricity instead - jets off the ankles and lines crawling over the body -
     * and costs a packet per particle, so it is worth it only for the mode meant to look violent.
     */
    private enum class Shape { CLOUD, ARCS }

    /**
     * How each mode is dressed.
     *
     * [perHundred] is how much a full-strength control adds over a minimum of one particle, so a
     * nudge and the real thing do not look alike. It is a [Shape.CLOUD] idea only: [Shape.ARCS]
     * carries intensity in how far the bolt climbs instead, and the particle count falls out of
     * that and [SPACING]. [volume] and [pitch] are the same idea for the sound, with [wobble]
     * spreading the pitch a little each time so a run of them reads as crackling rather than as a
     * stuck loop.
     *
     * [burstEveryTicks] is how often the particles are redrawn. A cloud only has to be topped up,
     * but arcs have to be redrawn every single tick - see [STRIKE_TICKS] for why that is the
     * difference between a bolt and a sprinkle of dots.
     *
     * [snapEveryTicks] has to match the length of the audio behind the event. A control can last
     * anywhere from a third of a second to thirty, so the sound is retriggered to cover it - and
     * retriggering faster than the sample is long just stacks it on top of itself. Short vanilla
     * stand-ins want a low number; a long recording wants roughly its own length.
     */
    private class Dressing(
        val particle: SimpleParticleType,
        val shape: Shape,
        val perHundred: Int,
        val burstEveryTicks: Long,
        val sound: SoundEvent,
        val volume: Float,
        val volumePerHundred: Float,
        val pitch: Float,
        val wobble: Float,
        val snapEveryTicks: Long,
    )

    /**
     * The sound each mode plays.
     *
     * The mod's own events from 1.21.4 on, which `assets/shockcraft/sounds.json` maps to whatever
     * audio it should actually be. Below that there is no collar, no remote and nothing
     * registering a sound event, so the closest vanilla sounds stand in directly.
     */
    //? if >=1.21.4 {
    private val shockSound: SoundEvent = ModContent.SHOCK_SOUND
    private val vibrateSound: SoundEvent = ModContent.VIBRATE_SOUND
    private val beepSound: SoundEvent = ModContent.BEEP_SOUND
    //?} else {
    /*private val shockSound: SoundEvent = SoundEvents.LIGHTNING_BOLT_IMPACT
    private val vibrateSound: SoundEvent = SoundEvents.BEE_LOOP
    private val beepSound: SoundEvent = SoundEvents.AMETHYST_BLOCK_CHIME
    *///?}

    /**
     * What a bolt is drawn out of.
     *
     * The mod's own streak wherever there is a registry to put it in, which is the same 1.21.4
     * floor the sounds and the items have - below that there is no [Content] at all, so a bolt
     * falls back to a line of vanilla sparks. It is the one place the effect is visibly worse on
     * the old versions, and the alternative was a second registration path for three targets.
     */
    //? if >=1.21.4 {
    private val arcParticle: SimpleParticleType = ModContent.SHOCK_ARC
    //?} else {
    /*private val arcParticle: SimpleParticleType = ParticleTypes.ELECTRIC_SPARK
    *///?}

    private val dressings: Map<RemoteMode, Dressing> = mapOf(
        // An arc: bright, fast, unmistakable, and drawn as actual lightning rather than as a puff.
        //
        // One bolt, every tick, climbing out of the shocker. Intensity is in how far up the body
        // it gets rather than in how many of them there are - a second bolt somewhere else does
        // not read as "harder", it reads as "noisier", and the count is what made the old version
        // look like glitter. Hence the zero: perHundred is a cloud idea and arcs do not use it.
        //
        // 89 ticks is `assets/shockcraft/sounds/shock.ogg` rounded *down* - it runs 4.463s, which
        // is 89.3 ticks - so a long shock retriggers a hair early and the loop overlaps by ~13ms
        // instead of leaving a gap. Change this with the file; a crackle that gaps reads as a
        // stutter, where a slight overlap reads as continuous.
        //
        // Barely any pitch wobble either. Detuning a real recording by a fifth each time, which
        // is what suited a repeated vanilla thunderclap, would just make it sound broken.
        RemoteMode.Shock to Dressing(
            arcParticle, Shape.ARCS, 0, BURST_TICKS,
            shockSound, 0.15f, 0.25f, 1.0f, 0.08f, 89L,
        ),
        // A buzz, pitched down and kept quiet so it reads as something on a leg.
        RemoteMode.Vibrate to Dressing(
            ParticleTypes.CRIT, Shape.CLOUD, 3, 2L,
            vibrateSound, 0.12f, 0.18f, 0.7f, 0.2f, 10L,
        ),
        // A beep: notes and a chime, standing in for the collar's own speaker.
        RemoteMode.Sound to Dressing(
            ParticleTypes.NOTE, Shape.CLOUD, 2, 2L,
            beepSound, 0.15f, 0.20f, 1.2f, 0.6f, 10L,
        ),
    )

    private class Crackle(
        val endsAtTick: Long,
        val mode: RemoteMode,
        val intensity: Int,
        val particles: Boolean,
        val sound: Boolean,
    ) {
        /**
         * When this crackle is next due a sound, scheduled per crackle rather than off a shared
         * phase so the first one lands on the tick the shock does. A shared phase would delay it
         * by up to a whole interval, which is unnoticeable at two a second and very noticeable
         * once an interval is measured in seconds.
         */
        var nextSnapTick: Long = Long.MIN_VALUE

    }

    /**
     * Keyed by player id rather than by [ServerPlayer], so an entry cannot pin a disconnected
     * player in memory. It is emptied by [tick] as crackles run out, and a player who logs out
     * mid-control is dropped the next time their entry comes up.
     */
    private val crackling = HashMap<UUID, Crackle>()

    private var tick: Long = 0

    /**
     * Something this player's own client has already had accepted by the backend.
     *
     * [particles] and [sound] are that player's own choice about what the room gets. A client that
     * wants neither does not send the packet at all, so both being false here is only ever a
     * hand-written one - and it is dropped rather than kept as a silent timer.
     */
    fun onShocked(
        player: ServerPlayer,
        mode: RemoteMode,
        intensity: Int,
        duration: Int,
        particles: Boolean,
        sound: Boolean,
    ) {
        if (!particles && !sound) return

        val durationMs = duration.coerceIn(0, MAX_DURATION_MS)
        if (durationMs == 0) return

        // At least one burst, so the shortest control the backend accepts still shows something.
        val ticks = (durationMs / 50L).coerceAtLeast(MIN_TICKS)

        crackling[player.getUUID()] =
            Crackle(tick + ticks, mode, intensity.coerceIn(0, 100), particles, sound)
    }

    /** Called at the end of every server tick, from both loaders' entrypoints. */
    fun tick(server: MinecraftServer) {
        tick++

        // The overwhelmingly common case, and it costs one field read.
        if (crackling.isEmpty()) return

        val entries = crackling.entries.iterator()
        while (entries.hasNext()) {
            val (id, crackle) = entries.next()

            // Gone or finished, either way there is nothing left to play for them.
            val player = server.playerList.getPlayer(id)
            if (player == null || tick >= crackle.endsAtTick) {
                entries.remove()

                // Removed first, so the "is anyone else using this sound" check below does not
                // count the crackle that has just ended.
                if (player != null && crackle.sound) cutOff(player, crackle.mode)
                continue
            }

            val dressing = dressings[crackle.mode] ?: continue

            if (crackle.particles && tick % dressing.burstEveryTicks == 0L) {
                burst(player, dressing, crackle.intensity)
            }

            if (crackle.sound && tick >= crackle.nextSnapTick) {
                crackle.nextSnapTick = tick + dressing.snapEveryTicks
                snap(player, dressing, crackle.intensity)
            }
        }
    }

    /** ServerPlayer.serverLevel() was folded into a covariant level() in 1.21.11. */
    private fun serverLevel(player: ServerPlayer) =
        //? if >=1.21.11 {
        player.level()
        //?} else {
        /*player.serverLevel()
        *///?}

    /** One frame of whatever this mode looks like, drawn fresh wherever the player is now. */
    private fun burst(player: ServerPlayer, dressing: Dressing, intensity: Int) {
        when (dressing.shape) {
            Shape.CLOUD -> cloud(player, dressing, intensity)
            Shape.ARCS -> arcs(player, dressing, intensity)
        }
    }

    /**
     * A small puff of particles at each shocker, which is all a buzz or a beep needs.
     *
     * At the cuffs rather than around the whole body, the same as the arcs: the buzz and the beep
     * come out of the hardware, and a cloud in the middle of the torso said nothing about where.
     *
     * One packet per ankle: with a positive count the three offsets are a gaussian spread that
     * the receiving client draws itself, so each puff costs what a single particle would. The
     * count is split between the two, rounded up, so neither ankle is ever left empty.
     */
    private fun cloud(player: ServerPlayer, dressing: Dressing, intensity: Int) {
        val level = serverLevel(player)

        val count = 1 + intensity * dressing.perHundred / 100
        val perCuff = (count + 1) / 2

        val radius = player.bbWidth * 0.3
        val yaw = Math.toRadians(player.yBodyRot.toDouble())
        val rightX = cos(yaw)
        val rightZ = sin(yaw)

        for (side in ANKLES) {
            // sendParticles is the vanilla broadcast: everyone within 32 blocks gets it, the
            // person it landed on included, so there is no packet of our own going back down.
            level.sendParticles(
                dressing.particle,
                player.x + rightX * side * radius * CUFF_OUT,
                player.y + CUFF_HEIGHT + 0.05,
                player.z + rightZ * side * radius * CUFF_OUT,
                perCuff,
                // Tight: a puff on the cuff, not a haze around the shin.
                0.07,
                0.05,
                0.07,
                // Barely any: the particles should sit on the cuff, not spray off it.
                0.03,
            )
        }
    }

    /**
     * A shock, drawn as an arc flash at the shocker.
     *
     * A handful of short arcs jumping off the cuffs on both ankles, thrown away and struck again
     * ten times a second. Each one is two overlapping streaks with a bend between them, pointing
     * off in its own direction - away from the leg and generally upwards, because that is what an
     * arc does: it jumps a gap, it does not follow the body around.
     *
     * The arcs are deliberately independent of one another and are not asked to join up. An
     * earlier version wound one long bolt up the body out of pieces that had to meet end to end,
     * and that is a shape Minecraft particles sell badly and a constraint that goes wrong in a
     * dozen ways. Short, many, fast and unconnected is both easier to draw and closer to what a
     * shocker actually does.
     *
     * Intensity buys two things: more arcs at once, and more room for them to appear in, so a
     * nudge crackles on the cuff itself and a full-strength control has them climbing the shin.
     * Neither makes any single arc bigger - a long arc is a lightning bolt, not a spark gap.
     *
     * Each piece is placed individually, since vanilla will only aim one at a time:
     * [ServerLevel.sendParticles] reads the three offsets as a spread when the count is positive
     * and as the velocity when it is zero. So this costs a packet each where [cloud] costs one in
     * total - about a dozen a burst at full strength, to everyone within 32 blocks.
     */
    private fun arcs(player: ServerPlayer, dressing: Dressing, intensity: Int) {
        val level = serverLevel(player)
        val rng = player.random

        val radius = player.bbWidth * 0.3

        // Yaw 0 faces +Z, which puts the player right at (cos, sin). Hanging the cuffs off that
        // rather than off a fixed compass direction keeps them on the ankles as the player turns.
        val yaw = Math.toRadians(player.yBodyRot.toDouble())
        val rightX = cos(yaw)
        val rightZ = sin(yaw)

        // How far above the cuff an arc may start.
        val spread = 0.04 + 0.34 * intensity / 100.0

        repeat(MIN_ARCS + intensity * ARCS_PER_HUNDRED / 100) {
            val side = ANKLES[rng.nextInt(ANKLES.size)]

            var x = player.x + rightX * side * radius * CUFF_OUT + rng.nextGaussian() * 0.03
            var y = player.y + CUFF_HEIGHT + rng.nextDouble() * spread
            var z = player.z + rightZ * side * radius * CUFF_OUT + rng.nextGaussian() * 0.03

            // Somewhere off the leg and mostly upward. Not normalised - the length of each piece
            // is set below, so this only has to point.
            val around = rng.nextDouble() * PI * 2
            var dx = cos(around) * 0.7 + rightX * side * 0.5
            var dy = 0.2 + rng.nextDouble() * 0.8
            var dz = sin(around) * 0.7 + rightZ * side * 0.5

            repeat(ARC_PIECES) {
                val length = PIECE_MIN + rng.nextDouble() * PIECE_VARY
                val scale = length / sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001)

                val toX = x + dx * scale
                val toY = y + dy * scale
                val toZ = z + dz * scale

                //? if >=1.21.4 {
                // The midpoint and the vector from one end to the other: ShockArcParticle reads
                // that as the length and the direction to lie along. It is not a velocity.
                level.spark(
                    dressing.particle,
                    (x + toX) * 0.5,
                    (y + toY) * 0.5,
                    (z + toZ) * 0.5,
                    toX - x,
                    toY - y,
                    toZ - z,
                )
                //?} else {
                /*// No registry below 1.21.4 and so no streak - a stationary vanilla spark at each
                // end instead, which at this size is a short bright dash rather than a line.
                level.spark(dressing.particle, x, y, z, 0.0, 0.0, 0.0)
                level.spark(dressing.particle, toX, toY, toZ, 0.0, 0.0, 0.0)
                *///?}

                x = toX
                y = toY
                z = toZ

                // The bend. Generous, because an arc that carries straight on is a needle.
                dx += rng.nextGaussian() * 0.6
                dy += rng.nextGaussian() * 0.6
                dz += rng.nextGaussian() * 0.6
            }
        }

        // Everything is at the shocker, and nothing else is drawn. An end rod "afterglow" used to
        // drift off strong shocks, and it read as stray white particles unrelated to the arcs -
        // they are pure white, float upward and outlive the shock by seconds.
    }

    /**
     * One particle, at exactly this point, moving exactly this way.
     *
     * A count of zero is what switches vanilla from scattering to aiming; the speed is folded
     * into the velocity here so callers only ever think in blocks per tick. See [arcs].
     */
    private fun ServerLevel.spark(
        particle: SimpleParticleType,
        x: Double,
        y: Double,
        z: Double,
        dx: Double,
        dy: Double,
        dz: Double,
    ) {
        sendParticles(particle, x, y, z, 0, dx, dy, dz, 1.0)
    }

    /**
     * Cuts the sound off when the control ends, so what is heard lasts as long as what is felt.
     *
     * Without this the audio outlives the shock by however much of the sample was left running:
     * a one-second shock would still play the whole four and a half seconds. The loop covers a
     * long control and this trims the tail off a short one.
     *
     * The packet is blunt - it stops *every* instance of that sound in that category on whoever
     * receives it, because vanilla gives the server no handle on a single playing sound. So it is
     * held back while anybody else is still crackling with the same one, and their tail is left to
     * run out on its own rather than silencing them mid-shock. Overrunning is the harmless
     * failure here; cutting somebody else off is not.
     */
    private fun cutOff(player: ServerPlayer, mode: RemoteMode) {
        val dressing = dressings[mode] ?: return
        if (crackling.values.any { it.sound && it.mode == mode }) return

        val packet = ClientboundStopSoundPacket(McCompat.soundId(dressing.sound), SoundSource.PLAYERS)

        // Everyone in the level rather than everyone in earshot: a client with nothing playing
        // ignores it, and working out who actually heard it costs more than the packet does.
        for (listener in serverLevel(player).players()) {
            listener.connection.send(packet)
        }
    }

    private fun snap(player: ServerPlayer, dressing: Dressing, intensity: Int) {
        val level = serverLevel(player)

        val volume = dressing.volume + intensity * dressing.volumePerHundred / 100f
        val pitch = dressing.pitch + (player.random.nextFloat() - 0.5f) * dressing.wobble

        // Bound to the player rather than played at their coordinates. A positional sound is
        // pinned to the spot it started at, so a control lasting seconds would be left behind the
        // moment its wearer walked away from it; this overload sends ClientboundSoundEntityPacket
        // instead, which every client renders as a sound that follows the entity.
        //
        // The particles have no such problem - each burst is spawned fresh at wherever the player
        // is that tick - which is why only the sound needed this.
        //
        // A null first argument means nobody is skipped, so the person it landed on hears it too.
        // Sent from the server like the particles, so there is no packet of our own going down.
        level.playSound(null, player, dressing.sound, SoundSource.PLAYERS, volume, pitch)
    }
}
