package openshock.integrations.minecraft.api

/**
 * What pressing a remote asks for.
 *
 * Deliberately narrower than [ControlType], which also has Stop - not something a remote should be
 * able to aim at somebody else's shockers. The wire and the item both carry this, and the widening
 * to a control type happens once, here.
 *
 * Stored on a remote by name rather than by ordinal. The component outlives any particular build,
 * so inserting a mode later must not silently turn every saved remote into a different one.
 */
enum class RemoteMode(val control: ControlType, val label: String) {
    Shock(ControlType.Shock, "Shock"),
    Vibrate(ControlType.Vibrate, "Vibrate"),
    Sound(ControlType.Sound, "Sound");

    /** The next mode round the loop, for a control that cycles rather than picks. */
    fun next(): RemoteMode = entries[(ordinal + 1) % entries.size]

    companion object {

        /** What a remote asks for until somebody changes it, and what an unreadable one falls back to. */
        val DEFAULT: RemoteMode = Shock

        /**
         * Never throws. An unknown name is an old remote, a hand-edited item or a packet from a
         * client that made one up, and none of those should be an exception - they press as a
         * plain shock, which is what every remote did before modes existed.
         */
        /**
         * The mode a control corresponds to, or null for one that is not a mode at all.
         *
         * Only Stop returns null. It is a thing the mod can send but not a thing a remote can ask
         * for, and stopping is the absence of an effect rather than one to dress.
         */
        fun fromControl(type: ControlType): RemoteMode? =
            entries.firstOrNull { it.control == type }

        fun byName(name: String?): RemoteMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
