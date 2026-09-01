package openshock.integrations.minecraft

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import openshock.integrations.minecraft.api.RemoteMode
import openshock.integrations.minecraft.platform.McCompat
import java.util.UUID
//? if >=1.21.5 {
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


    /**
     * How each mode is dressed.
     *
     * [perHundred] is how many extra particles a full-strength control adds over a minimum of one,
     * so a nudge and the real thing do not look alike. [volume] and [pitch] are the same idea for
     * the sound, with [wobble] spreading the pitch a little each time so a run of them reads as
     * crackling rather than as a stuck loop.
     *
     * [snapEveryTicks] has to match the length of the audio behind the event. A control can last
     * anywhere from a third of a second to thirty, so the sound is retriggered to cover it - and
     * retriggering faster than the sample is long just stacks it on top of itself. Short vanilla
     * stand-ins want a low number; a long recording wants roughly its own length.
     */
    private class Dressing(
        val particle: SimpleParticleType,
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
     * The mod's own events from 1.21.5 on, which `assets/shockcraft/sounds.json` maps to whatever
     * audio it should actually be. Below that there is no collar, no remote and nothing
     * registering a sound event, so the closest vanilla sounds stand in directly.
     */
    //? if >=1.21.5 {
    private val shockSound: SoundEvent = ModContent.SHOCK_SOUND
    private val vibrateSound: SoundEvent = ModContent.VIBRATE_SOUND
    private val beepSound: SoundEvent = ModContent.BEEP_SOUND
    //?} else {
    /*private val shockSound: SoundEvent = SoundEvents.LIGHTNING_BOLT_IMPACT
    private val vibrateSound: SoundEvent = SoundEvents.BEE_LOOP
    private val beepSound: SoundEvent = SoundEvents.AMETHYST_BLOCK_CHIME
    *///?}

    private val dressings: Map<RemoteMode, Dressing> = mapOf(
        // An arc: bright, fast, unmistakable.
        //
        // 89 ticks is `assets/shockcraft/sounds/shock.ogg` rounded *down* - it runs 4.463s, which
        // is 89.3 ticks - so a long shock retriggers a hair early and the loop overlaps by ~13ms
        // instead of leaving a gap. Change this with the file; a crackle that gaps reads as a
        // stutter, where a slight overlap reads as continuous.
        //
        // Barely any pitch wobble either. Detuning a real recording by a fifth each time, which
        // is what suited a repeated vanilla thunderclap, would just make it sound broken.
        RemoteMode.Shock to Dressing(
            ParticleTypes.ELECTRIC_SPARK, 5,
            shockSound, 0.15f, 0.25f, 1.0f, 0.08f, 89L,
        ),
        // A buzz, pitched down and kept quiet so it reads as something on a leg.
        RemoteMode.Vibrate to Dressing(
            ParticleTypes.CRIT, 3,
            vibrateSound, 0.12f, 0.18f, 0.7f, 0.2f, 10L,
        ),
        // A beep: notes and a chime, standing in for the collar's own speaker.
        RemoteMode.Sound to Dressing(
            ParticleTypes.NOTE, 2,
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

    private fun burst(player: ServerPlayer, dressing: Dressing, intensity: Int) {
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

        // A null source means nobody is skipped, so the person it landed on hears it too. Sent
        // from the server like the particles, so there is no packet of our own going back down.
        level.playSound(
            null,
            player.x,
            player.y + player.bbHeight * 0.5,
            player.z,
            dressing.sound,
            SoundSource.PLAYERS,
            volume,
            pitch,
        )
    }
}
