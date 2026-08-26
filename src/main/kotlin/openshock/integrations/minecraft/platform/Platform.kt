package openshock.integrations.minecraft.platform

import java.nio.file.Path

//? if fabric {
/*import net.fabricmc.loader.api.FabricLoader

/** The handful of things the two loaders disagree on outside of the entrypoints. */
object Platform {
    val configDir: Path get() = FabricLoader.getInstance().configDir
}
*///?} elif neoforge {
import net.neoforged.fml.loading.FMLPaths

/** The handful of things the two loaders disagree on outside of the entrypoints. */
object Platform {
    val configDir: Path get() = FMLPaths.CONFIGDIR.get()
}
//?}
