package openshock.integrations.minecraft.utils

/**
 * The short, human-readable form of a UUID, e.g. `A3F-92C`.
 *
 * A collar and every remote bound to it show this, and that shared string is the whole of "which
 * remote controls which collar". Six hex characters is one in sixteen million - far beyond enough
 * to tell apart the handful of collars anyone has in front of them - and it is only ever a label.
 * Matching is always done on the full id.
 *
 * Lives out here rather than beside the items because the shock gate names shocks with it too, and
 * that runs on versions where the items do not exist.
 */
fun shortCode(id: String): String =
    id.filter { it != '-' }.take(6).uppercase().chunked(3).joinToString("-")
