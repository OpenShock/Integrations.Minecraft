package openshock.integrations.minecraft.platform

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ModIdentifier
*///?} else {
import net.minecraft.resources.ResourceLocation as ModIdentifier
//?}

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
            /*return Minecraft.getInstance().gui.screen()
            *///?} else {
            return Minecraft.getInstance().screen
            //?}
        }

    /** `displayClientMessage(text, true)` was split out into `sendOverlayMessage(text)` in 26.1. */
    fun sendActionBar(text: Component) {
        val player = Minecraft.getInstance().player ?: return
        //? if >=26.1 {
        /*player.sendOverlayMessage(text)
        *///?} else {
        player.displayClientMessage(text, true)
        //?}
    }

    /**
     * `ResourceLocation` was renamed to `Identifier` in 1.21.11 (handled by the import alias), and
     * 1.21 replaced its public constructor with the `fromNamespaceAndPath` factory.
     */
    fun identifier(namespace: String, path: String): ModIdentifier {
        //? if >=1.21 {
        /*return ModIdentifier.fromNamespaceAndPath(namespace, path)
        *///?} else {
        return ModIdentifier(namespace, path)
        //?}
    }
}
