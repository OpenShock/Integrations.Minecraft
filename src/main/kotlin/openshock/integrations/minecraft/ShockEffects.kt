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

    /** 10 bursts a second: dense enough to read as continuous, sparse enough to stay cheap. */
    private const val TICKS_BETWEEN_BURSTS = 2L

    /** The two ankles the shocker sits on, as a sign along the player's right. */
    private val ANKLES = intArrayOf(-1, 1)

    /** How far around the body one arc travels at most, in radians - a bit over a third. */
    private const val ARC_SWEEP = 2.4

    /** Segments per arc; the particle count is one more than this. Three reads as a line. */
    private const val ARC_STEPS = 3

    /** Below this a shock is a twitch, and an afterglow would oversell it. */
    private const val MOTE_FROM_INTENSITY = 50

    /** One burst in five, so a strong shock sheds about two motes a second. */
    private const val MOTE_ONE_IN = 5


    /**
     * How a mode arranges its particles.
     *
     * [CLOUD] is a puff around the player, which is all a buzz or a beep needs to be visible.
     * [ARCS] draws electricity instead - jets off the ankles and lines crawling over the body -
     * and costs a packet per particle, so it is worth it only for the mode meant to look violent.
     */
    private enum class Shape { CLOUD, ARCS }

    /**
     * How each mode is dressed.
     *
     * [perHundred] is how much a full-strength control adds over a minimum of one, so a nudge and
     * the real thing do not look alike - particles under [Shape.CLOUD], whole arcs under
     * [Shape.ARCS], which is why the shock's number is so much smaller than it looks. [volume] and
     * [pitch] are the same idea for the sound, with [wobble] spreading the pitch a little each
     * time so a run of them reads as crackling rather than as a stuck loop.
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

    private val dressings: Map<RemoteMode, Dressing> = mapOf(
        // An arc: bright, fast, unmistakable, and drawn as actual arcs rather than as a puff.
        //
        // Two per hundred, so a 100% shock crawls with three at once and a 10% one has a single
        // arc flickering somewhere on the body. Any more and they stop being legible as lines.
        //
        // 89 ticks is `assets/shockcraft/sounds/shock.ogg` rounded *down* - it runs 4.463s, which
        // is 89.3 ticks - so a long shock retriggers a hair early and the loop overlaps by ~13ms
        // instead of leaving a gap. Change this with the file; a crackle that gaps reads as a
        // stutter, where a slight overlap reads as continuous.
        //
        // Barely any pitch wobble either. Detuning a real recording by a fifth each time, which
        // is what suited a repeated vanilla thunderclap, would just make it sound broken.
        RemoteMode.Shock to Dressing(
            ParticleTypes.ELECTRIC_SPARK, Shape.ARCS, 2,
            shockSound, 0.15f, 0.25f, 1.0f, 0.08f, 89L,
        ),
        // A buzz, pitched down and kept quiet so it reads as something on a leg.
        RemoteMode.Vibrate to Dressing(
            ParticleTypes.CRIT, Shape.CLOUD, 3,
            vibrateSound, 0.12f, 0.18f, 0.7f, 0.2f, 10L,
        ),
        // A beep: notes and a chime, standing in for the collar's own speaker.
        RemoteMode.Sound to Dressing(
            ParticleTypes.NOTE, Shape.CLOUD, 2,
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
        val ticks = (durationMs / 50L).coerceAtLeast(TICKS_BETWEEN_BURSTS)

        crackling[player.getUUID()] =
            Crackle(tick + ticks, mode, intensity.coerceIn(0, 100), particles, sound)
    }

    /** Called at the end of every server tick, from both loaders' entrypoints. */
    fun tick(server: MinecraftServer) {
        tick++

        // The overwhelmingly common case, and it costs one field read.
        if (crackling.isEmpty()) return

        val drawing = tick % TICKS_BETWEEN_BURSTS == 0L

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

            if (drawing && crackle.particles) burst(player, dressing, crackle.intensity)

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
     * A puff of particles standing in the player, which is all a buzz or a beep needs.
     *
     * One packet for the lot: with a positive count the three offsets are a gaussian spread that
     * the receiving client draws itself, so the whole cloud costs what a single particle would.
     */
    private fun cloud(player: ServerPlayer, dressing: Dressing, intensity: Int) {
        val level = serverLevel(player)

        val count = 1 + intensity * dressing.perHundred / 100

        // sendParticles is the vanilla broadcast: everyone within 32 blocks gets it, the person
        // it landed on included, so there is no packet of our own going back down to the client.
        level.sendParticles(
            dressing.particle,
            player.x,
            player.y + player.bbHeight * 0.5,
            player.z,
            count,
            player.bbWidth * 0.4,
            player.bbHeight * 0.35,
            player.bbWidth * 0.4,
            // Barely any: the particles should sit on the player, not spray off them.
            0.05,
        )
    }

    /**
     * A shock, drawn as electricity instead of as confetti.
     *
     * Three things go on at once. Sparks jet off both ankles, because that is where the shocker
     * actually sits on the model, so the effect starts where the hardware is rather than in the
     * middle of the torso. Arcs crawl across the body: each one is a chord between two points on
     * the cylinder around the player, bowed outwards so it stands off them, and drawn as a line
     * of sparks left hanging in place - stationary is the whole trick, because a spark that
     * drifts stops reading as part of a line. And a strong control sheds the odd end rod mote
     * floating away, so a full-strength shock has an afterglow that a nudge does not.
     *
     * Every particle is placed individually, since vanilla will only let you aim one at a time:
     * [ServerLevel.sendParticles] reads the three offsets as a spread when the count is positive
     * and as the velocity when it is zero. So this costs a packet each where [cloud] costs one
     * in total - fine for the handful below, which only go out while somebody is being shocked
     * and only to players within 32 blocks, but the reason the counts stay small.
     */
    private fun arcs(player: ServerPlayer, dressing: Dressing, intensity: Int) {
        val level = serverLevel(player)
        val rng = player.random

        val height = player.bbHeight.toDouble()
        val radius = player.bbWidth * 0.3

        // Yaw 0 faces +Z, which puts the player's right at (cos, sin). Hanging the jets off that
        // rather than off a fixed compass direction keeps them on the ankles as the player turns.
        val yaw = Math.toRadians(player.yBodyRot.toDouble())
        val rightX = cos(yaw)
        val rightZ = sin(yaw)

        for (side in ANKLES) {
            level.spark(
                dressing.particle,
                player.x + rightX * side * radius * 0.7,
                player.y + 0.1,
                player.z + rightZ * side * radius * 0.7,
                rightX * side * 0.25 + rng.nextGaussian() * 0.05,
                0.08 + rng.nextDouble() * 0.1,
                rightZ * side * 0.25 + rng.nextGaussian() * 0.05,
            )
        }

        repeat(1 + intensity * dressing.perHundred / 100) {
            val fromAngle = rng.nextDouble() * PI * 2
            val toAngle = fromAngle + (rng.nextDouble() - 0.5) * ARC_SWEEP
            val fromY = 0.15 + rng.nextDouble() * (height - 0.3)
            val toY = (fromY + (rng.nextDouble() - 0.5) * height).coerceIn(0.1, height - 0.1)

            for (step in 0..ARC_STEPS) {
                val along = step.toDouble() / ARC_STEPS
                val angle = fromAngle + (toAngle - fromAngle) * along

                // Widest in the middle, so the arc bows away from the body instead of tracing it.
                val out = radius * (1.0 + 0.4 * sin(along * PI))

                level.spark(
                    dressing.particle,
                    player.x + cos(angle) * out,
                    player.y + fromY + (toY - fromY) * along,
                    player.z + sin(angle) * out,
                    rng.nextGaussian() * 0.01,
                    rng.nextGaussian() * 0.01,
                    rng.nextGaussian() * 0.01,
                )
            }
        }

        if (intensity >= MOTE_FROM_INTENSITY && rng.nextInt(MOTE_ONE_IN) == 0) {
            level.spark(
                ParticleTypes.END_ROD,
                player.x + (rng.nextDouble() - 0.5) * radius * 2,
                player.y + 0.2 + rng.nextDouble() * (height - 0.4),
                player.z + (rng.nextDouble() - 0.5) * radius * 2,
                rng.nextGaussian() * 0.02,
                0.02 + rng.nextDouble() * 0.03,
                rng.nextGaussian() * 0.02,
            )
        }
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
