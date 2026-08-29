package openshock.integrations.minecraft.api

/**
 * One shocker the configured API token is allowed to control, flattened out of the hub (and, for
 * shared ones, owner) nesting that the listing endpoints return.
 */
data class Shocker(
    val id: String,
    val name: String,
    val hub: String,
    /** Null for the account's own shockers, the owner's display name for ones shared with it. */
    val owner: String?,
    val paused: Boolean,
)

/*
 * Gson mirrors of GET /1/shockers/own and GET /1/shockers/shared. Only the fields we actually use
 * are declared - Gson ignores the rest - and every one of them is nullable: Gson fills these in
 * reflectively without running the initialisers, so a field a backend stops sending would
 * otherwise leave a non-null Kotlin property holding null.
 */

internal class OwnShockersResponse {
    val data: List<ApiHub>? = null
}

internal class SharedShockersResponse {
    val data: List<ApiOwner>? = null
}

internal class ApiOwner {
    val name: String? = null
    val devices: List<ApiHub>? = null
}

internal class ApiHub {
    val name: String? = null
    val shockers: List<ApiShocker>? = null
}

internal class ApiShocker {
    val id: String? = null
    val name: String? = null
    val isPaused: Boolean = false
}

/** Shockers without an id are unusable, so they are dropped rather than shown as unpickable. */
internal fun ApiHub.toShockers(owner: String?): List<Shocker> =
    shockers.orEmpty().mapNotNull { shocker ->
        val id = shocker.id ?: return@mapNotNull null
        Shocker(id, shocker.name ?: id, name ?: "Unknown hub", owner, shocker.isPaused)
    }
