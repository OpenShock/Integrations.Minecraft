package openshock.integrations.minecraft.platform

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.damagesource.DamageSource

//? if >=1.21.11 {
import net.minecraft.resources.Identifier as ModIdentifier
//?} else {
/*import net.minecraft.resources.ResourceLocation as ModIdentifier
*///?}

/**
 * The only place where vanilla's own API drifts across the supported Minecraft versions.
 * Everything else in the mod compiles unchanged from 1.21.1 all the way to 26.2, so when a new
 * Minecraft version breaks something, this file is the first (and usually only) thing to touch.
 */
object McCompat {

    /** `Minecraft.screen` moved onto `Gui` in 26.2. */
    val currentScreen: Screen?
        get() {
            //? if >=26.2 {
            return Minecraft.getInstance().gui.screen()
            //?} else {
            /*return Minecraft.getInstance().screen
            *///?}
        }

    /** `Minecraft.setScreen` moved onto `Gui` in 26.2, the same way [currentScreen] did. */
    fun setScreen(screen: Screen) {
        //? if >=26.2 {
        Minecraft.getInstance().gui.setScreen(screen)
        //?} else {
        /*Minecraft.getInstance().setScreen(screen)
        *///?}
    }

    /** Closing is `setScreen(null)`, which moved onto `Gui` in 26.2 along with the rest. */
    fun closeScreen() {
        //? if >=26.2 {
        Minecraft.getInstance().gui.setScreen(null)
        //?} else {
        /*Minecraft.getInstance().setScreen(null)
        *///?}
    }

    /** `displayClientMessage(text, true)` was split out into `sendOverlayMessage(text)` in 26.1. */
    fun sendActionBar(text: Component) {
        val player = Minecraft.getInstance().player ?: return
        //? if >=26.1 {
        player.sendOverlayMessage(text)
        //?} else {
        /*player.displayClientMessage(text, true)
        *///?}
    }

    /**
     * The registry id of what hurt us, e.g. `minecraft:cactus`.
     *
     * Null when the client was never told: the damage type travels in the damage packet, so a hit
     * we only noticed as health going down has nothing to look up.
     *
     * `ResourceKey.location` was renamed to `identifier` in 1.21.11, along with the type it returns.
     */
    fun damageTypeId(source: DamageSource?): String? {
        val key = source?.typeHolder()?.unwrapKey()?.orElse(null) ?: return null

        //? if >=1.21.11 {
        return key.identifier().toString()
        //?} else {
        /*return key.location().toString()
        *///?}
    }

    /**
     * Every damage type id the world we are in knows, sorted, or empty when there is no world.
     *
     * The damage type registry is sent by the server, so this is the only way to see the ones a
     * datapack or another mod added - and the reason the config screen can only offer the exact
     * type list while in a world.
     *
     * `RegistryAccess.registryOrThrow` was renamed to `lookupOrThrow` in 1.21.4.
     */
    fun damageTypeIds(): List<String> {
        val level = Minecraft.getInstance().level ?: return emptyList()

        //? if >=1.21.4 {
        val registry = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
        //?} else {
        /*val registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
        *///?}

        return registry.keySet().map { it.toString() }.sorted()
    }

    /**
     * `ResourceLocation` was renamed to `Identifier` in 1.21.11 (handled by the import alias), and
     * 1.21 replaced its public constructor with the `fromNamespaceAndPath` factory.
     */
    fun identifier(namespace: String, path: String): ModIdentifier {
        //? if >=1.21 {
        return ModIdentifier.fromNamespaceAndPath(namespace, path)
        //?} else {
        /*return ModIdentifier(namespace, path)
        *///?}
    }
}
