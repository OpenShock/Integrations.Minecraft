package openshock.integrations.minecraft

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick
import net.minecraft.client.MinecraftClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ShockCraft : ModInitializer {
    public val logger: Logger = LoggerFactory.getLogger("shockcraft")

	override fun onInitialize() {
		logger.info("Hello Fabric world!")

		ShockCraftConfig.HANDLER.load()
		ShockCraftConfig.HANDLER.save()

		ClientTickEvents.END_CLIENT_TICK.register(EndTick { ClientTickLoopFun() })
	}

	var lastTickHealth: Float = 20f

	private fun Reset() {
		lastTickHealth = 20f
	}

	private fun ClientTickLoopFun() {
		val player = MinecraftClient.getInstance().player

		// Player does not exist, reset and return
		if(player == null) {
			Reset()
			return
		}

		// No need to run if we are paused or in escape screen at least
		if(MinecraftClient.getInstance().isPaused) return

		// We usually cannot take damage in creative or spectator, reset and return
		if(player.isCreative || player.isSpectator) {
			Reset()
			return
		}

		val damageSinceLastTick = (lastTickHealth - player.health).coerceAtLeast(0f)

		// Set last tick health, we already calculated what we need
		lastTickHealth = player.health

		// Did we take damage?
		if(damageSinceLastTick > 0) {
			logger.info(player.recentDamageSource?.name + " - " + damageSinceLastTick.toString())

			if(player.isDead) logger.info("We also died in this tick")
		}
	}
}