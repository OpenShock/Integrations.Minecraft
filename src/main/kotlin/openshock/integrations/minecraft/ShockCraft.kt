package openshock.integrations.minecraft

import com.mojang.brigadier.CommandDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import openshock.integrations.minecraft.config.ShockCraftConfig
import openshock.integrations.minecraft.openshock.ControlType
import openshock.integrations.minecraft.openshock.OpenShockApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ShockCraft : ModInitializer {
    public val logger: Logger = LoggerFactory.getLogger("shockcraft")

	@OptIn(DelicateCoroutinesApi::class)
	override fun onInitialize() {
		logger.info("Hello Fabric world!")

		ShockCraftConfig.HANDLER.load()

		CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher: CommandDispatcher<ServerCommandSource?>, registryAccess: CommandRegistryAccess?, environment: CommandManager.RegistrationEnvironment? ->
			dispatcher.register(literal("foo")
				.executes { context ->

					GlobalScope.launch {  OpenShockApi.control(ControlType.Vibrate, 50, 5000u) }

					context.getSource().sendFeedback(
						{ Text.literal("Called /foo with no arguments") },
						false
					)
					1
				})
		})

		ClientTickEvents.END_CLIENT_TICK.register(EndTick { clientTickLoopFun() })
	}

	var lastTickHealth: Float = 20f
	var lastTickReset: Boolean = false

	private fun reset() {
		lastTickReset = true
		val player = MinecraftClient.getInstance().player

		if(player == null){
			lastTickHealth = 20f
			return
		}

		lastTickHealth = player.maxHealth
	}

	@OptIn(DelicateCoroutinesApi::class)
	private fun clientTickLoopFun() {
		val player = MinecraftClient.getInstance().player

		// Player does not exist, reset and return
		if(player == null) {
			reset()
			return
		}

		// No need to run if we are paused or in escape screen at least
		if(MinecraftClient.getInstance().isPaused) return

		val creativeOrSpectator = player.isCreative || player.isSpectator

		// We usually cannot take damage in creative or spectator, reset and return
		if(creativeOrSpectator) {
			reset()
			return
		}

		// This needs to be after all possible resets
		if(lastTickReset) {
			lastTickReset = false
			lastTickHealth = player.health
		}

		val damageSinceLastTick = (lastTickHealth - player.health).coerceAtLeast(0f)

		// Set last tick health, we already calculated what we need
		lastTickHealth = player.health

		// Did we take damage?
		if(damageSinceLastTick > 0) {
			logger.info(player.recentDamageSource?.name + " - " + damageSinceLastTick.toString())

			if(player.isDead) {
				GlobalScope.launch {
					onDeath(player)
				}
				return
			}
		}
	}

	private suspend fun onDeath(player: ClientPlayerEntity) {
		val config = ShockCraftConfig.HANDLER.instance()
		if (!config.onDeath) return
		var customName: String? = null
		if (player.recentDamageSource != null) {
			customName = player.recentDamageSource?.name + " (Integrations.Minecraft)"
		}
		OpenShockApi.control(
			ControlType.Shock,
			config.onDeathIntensity,
			config.onDeathDuration,
			customName
		)
	}

	private suspend fun onDamage(player: ClientPlayerEntity) {
		val config = ShockCraftConfig.HANDLER.instance()
		if (!config.onDamage) return

		var customName: String? = null
		if (player.recentDamageSource != null) {
			customName = player.recentDamageSource?.name + " (Integrations.Minecraft)"
		}
		OpenShockApi.control(
			ControlType.Shock,
			config.onDeathIntensity,
			config.onDeathDuration,
			customName
		)
	}
}