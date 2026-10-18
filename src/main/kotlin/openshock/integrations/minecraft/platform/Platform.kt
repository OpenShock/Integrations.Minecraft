package openshock.integrations.minecraft.platform

import openshock.integrations.minecraft.ShockCraft
import java.nio.file.Path

//? if fabric {
import net.fabricmc.loader.api.FabricLoader

/** The handful of things the two loaders disagree on outside of the entrypoints. */
object Platform {
    val configDir: Path get() = FabricLoader.getInstance().configDir

    const val loaderName: String = "Fabric"

    val loaderVersion: String by lazy { modVersion("fabricloader") }

    val shockCraftVersion: String by lazy { modVersion(ShockCraft.MOD_ID) }

    private fun modVersion(id: String): String = FabricLoader.getInstance().getModContainer(id)
        .map { it.metadata.version.friendlyString }
        .orElse("unknown")
}
//?} elif neoforge {
/*import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths

/** The handful of things the two loaders disagree on outside of the entrypoints. */
object Platform {
    val configDir: Path get() = FMLPaths.CONFIGDIR.get()

    const val loaderName: String = "NeoForge"

    val loaderVersion: String by lazy { modVersion("neoforge") }

    val shockCraftVersion: String by lazy { modVersion(ShockCraft.MOD_ID) }

    private fun modVersion(id: String): String = ModList.get().getModContainerById(id)
        .map { it.modInfo.version.toString() }
        .orElse("unknown")
}
*///?}
