package openshock.integrations.minecraft.platform

//? if fabric {
//? if >=1.21.4 {
// The same Fabric API overhaul that renamed ItemGroupEvents renamed this: ParticleFactoryRegistry
// became ParticleProviderRegistry, and the sprite sheet handed to the lambda came with it. Both
// spellings hand back something that is a vanilla SpriteSet, which is all Provider wants.
//? if >=26.1 {
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry
//?} else {
/*import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
*///?}
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.CreativeModeTabs
import openshock.integrations.minecraft.ShockArcParticle
import openshock.integrations.minecraft.content.ModContent

// Fabric API 6 (26.1) replaced ItemGroupEvents with CreativeModeTabEvents, the same bundle that
// renamed the payload registries.
//? if >=26.1 {
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
//?} else {
/*import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
*///?}

/**
 * Puts the items and components into the registries.
 *
 * The objects themselves live in [ModContent]; only the handing-over is loader work, and the two
 * loaders disagree about it completely - Fabric writes straight into the registry during mod init,
 * NeoForge refuses and makes you wait for an event.
 *
 * Runs on both sides. Items have to exist on a server as much as on a client, or an item stack
 * cannot cross between them.
 */
object Content {

    fun init() {
        for ((key, component) in ModContent.componentEntries) {
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, key, component)
        }

        for ((key, item) in ModContent.itemEntries) {
            Registry.register(BuiltInRegistries.ITEM, key, item)
        }

        // On both sides: the server picks which sound to play and names it by registry id, so a
        // client that has not registered it has nothing to resolve and hears nothing.
        for ((key, sound) in ModContent.soundEntries) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, key, sound)
        }

        // Also both sides, and for the same reason: the server names the particle by registry id
        // and a client that has not registered it drops the packet on the floor.
        for ((key, particle) in ModContent.particleEntries) {
            Registry.register(BuiltInRegistries.PARTICLE_TYPE, key, particle)
        }
    }

    /**
     * Client-only: the creative menu does not exist on a server.
     *
     * Tools and Utilities rather than Combat - a collar is worn by the person being shocked, which
     * makes it a tool of somebody else's, and Combat implies it is a weapon you point at people.
     */
    fun initClient() {
        // What actually draws a shock arc. The type above is only a name until something here
        // says what to build when one arrives.
        //? if >=26.1 {
        ParticleProviderRegistry.getInstance().register(ModContent.SHOCK_ARC) { sprites ->
            ShockArcParticle.Provider(sprites)
        }
        //?} else {
        /*ParticleFactoryRegistry.getInstance().register(ModContent.SHOCK_ARC) { sprites ->
            ShockArcParticle.Provider(sprites)
        }
        *///?}

        //? if >=26.1 {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { output ->
            output.accept(ModContent.COLLAR)
            output.accept(ModContent.REMOTE)
        }
        //?} else {
        /*ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(ModContent.COLLAR)
            entries.accept(ModContent.REMOTE)
        }
        *///?}
    }
}
//?} else {
/*/** Stub below 1.21.4, which has no Equippable component and therefore no collar. */
object Content {
    fun init() {}
    fun initClient() {}
}
*///?}
//?} elif neoforge {
/*//? if >=1.21.4 {
import net.neoforged.fml.ModLoadingContext
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent
import net.neoforged.neoforge.registries.RegisterEvent
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.CreativeModeTabs
import openshock.integrations.minecraft.ShockArcParticle
import openshock.integrations.minecraft.content.ModContent

/**
 * Puts the items and components into the registries.
 *
 * The objects themselves live in [ModContent]; only the handing-over is loader work. NeoForge
 * keeps its registries frozen outside RegisterEvent, so unlike Fabric this cannot simply be done
 * during init - it is queued on the same mod bus the payload types go through.
 *
 * Runs on both sides. Items have to exist on a server as much as on a client, or an item stack
 * cannot cross between them.
 */
object Content {

    fun init() {
        val bus = ModLoadingContext.get().activeContainer.eventBus ?: return

        bus.addListener(RegisterEvent::class.java) { event ->
            event.register(Registries.DATA_COMPONENT_TYPE) { helper ->
                for ((key, component) in ModContent.componentEntries) helper.register(key, component)
            }

            event.register(Registries.ITEM) { helper ->
                for ((key, item) in ModContent.itemEntries) helper.register(key, item)
            }

            // On both sides: the server picks which sound to play and names it by registry id, so
            // a client that has not registered it has nothing to resolve and hears nothing.
            event.register(Registries.SOUND_EVENT) { helper ->
                for ((key, sound) in ModContent.soundEntries) helper.register(key, sound)
            }

            // Also both sides, and for the same reason: the server names the particle by registry
            // id and a client that has not registered it drops the packet on the floor.
            event.register(Registries.PARTICLE_TYPE) { helper ->
                for ((key, particle) in ModContent.particleEntries) helper.register(key, particle)
            }
        }
    }

    /**
     * Client-only: the creative menu does not exist on a server.
     *
     * Tools and Utilities rather than Combat - a collar is worn by the person being shocked, which
     * makes it a tool of somebody else's, and Combat implies it is a weapon you point at people.
     */
    fun initClient() {
        val bus = ModLoadingContext.get().activeContainer.eventBus ?: return

        bus.addListener(BuildCreativeModeTabContentsEvent::class.java) { event ->
            if (event.tabKey != CreativeModeTabs.TOOLS_AND_UTILITIES) return@addListener
            event.accept(ModContent.COLLAR)
            event.accept(ModContent.REMOTE)
        }

        // What actually draws a shock arc. The type above is only a name until something here
        // says what to build when one arrives.
        bus.addListener(RegisterParticleProvidersEvent::class.java) { event ->
            event.registerSpriteSet(ModContent.SHOCK_ARC) { sprites ->
                ShockArcParticle.Provider(sprites)
            }
        }
    }
}
//?} else {
/*/** Stub below 1.21.4, which has no Equippable component and therefore no collar. */
object Content {
    fun init() {}
    fun initClient() {}
}
*///?}
*///?}
