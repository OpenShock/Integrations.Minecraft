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
import net.minecraft.client.gui.screen.GameMenuScreen
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.entity.damage.DamageSource
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import okhttp3.internal.wait
import openshock.integrations.minecraft.config.DamageShockMode
import openshock.integrations.minecraft.config.ShockCraftConfig
import openshock.integrations.minecraft.api.ControlType
import openshock.integrations.minecraft.api.OpenShockApi
import openshock.integrations.minecraft.utils.MathUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Calendar

object ShockCraft : ModInitializer {
    public val logger: Logger = LoggerFactory.getLogger("shockcraft")

    override fun onInitialize() {
        logger.info("Hello Fabric world!")

        ShockCraftConfig.HANDLER.load()

        ClientTickEvents.END_CLIENT_TICK.register(EndTick { clientTickLoopFun() })
    }

    var lastTickHealth: Float = 20f
    var lastTickReset: Boolean = false
    var pauseMenuOpen: Boolean = true

    private fun reset() {
        lastTickReset = true
        val player = MinecraftClient.getInstance().player

        if (player == null) {
            lastTickHealth = 20f
            return
        }

        lastTickHealth = player.maxHealth
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun clientTickLoopFun() {
        val currentScreen = MinecraftClient.getInstance().currentScreen;

        // Cursed if logic to see if pause menu was opened, might not work with all mods
        if (currentScreen != null) {
            if (!pauseMenuOpen && currentScreen is GameMenuScreen) {
                pauseMenuOpen = true
                logger.debug("Game menu opened")
            }
        } else if (pauseMenuOpen) {
            pauseMenuOpen = false
            logger.debug("Game menu closed")
        }

        // Pause menu is open or one of its childs. Reset so we dont shock when you close it again and have taken damage
        if(pauseMenuOpen) {
            reset()
            return
        }

        val player = MinecraftClient.getInstance().player

        // Player does not exist, reset and return
        if (player == null) {
            reset()
            return
        }

        val creativeOrSpectator = player.isCreative || player.isSpectator

        // We usually cannot take damage in creative or spectator, reset and return
        if (creativeOrSpectator) {
            reset()
            return
        }

        // This needs to be after all possible resets
        if (lastTickReset) {
            lastTickReset = false
            lastTickHealth = player.health
        }

        val damageSinceLastTick = (lastTickHealth - player.health).coerceAtLeast(0f)

        // Set last tick health, we already calculated what we need
        lastTickHealth = player.health

        // Did we take damage?
        if (damageSinceLastTick > 0) {
            logger.debug(player.recentDamageSource?.name + " - " + damageSinceLastTick.toString())

            if (player.isDead) {
                logger.debug("Player died")
                GlobalScope.launch {
                    onDeath(player)
                }
                return
            }

            GlobalScope.launch { onDamage(player, damageSinceLastTick) }
        }
    }

    private suspend fun onDeath(player: ClientPlayerEntity) {
        val config = ShockCraftConfig.HANDLER.instance()
        if (!config.onDeath) return

        OpenShockApi.control(
            ControlType.Shock,
            config.onDeathIntensity,
            config.onDeathDuration,
            getName(player.recentDamageSource)
        )
    }

    private var lastShock: Long = -1

    private suspend fun onDamage(player: ClientPlayerEntity, damage: Float) {
        val config = ShockCraftConfig.HANDLER.instance()
        if (!config.onDamage) return

        val currentTime = Calendar.getInstance().timeInMillis
        if (lastShock + config.cooldown.toLong() > currentTime) {
            logger.info("OnDamage is on cooldown")
            return
        }

        lastShock = currentTime

        val percentageThreshold = config.damageThreshold.toFloat() / 20f

        val intensity: Byte
        val duration: UShort

        when (config.damageMode) {
            DamageShockMode.LowHp -> {
                val percentageDamage = 1 - (player.health / player.maxHealth).coerceAtLeast(0f).coerceAtMost(1f)
                if (percentageDamage < percentageThreshold) {
                    logger.debug("Damage percentage is below threshold")
                    return
                }

                intensity = MathUtils.lerp(config.intensityMin, config.intensityMax, percentageDamage)
                duration = MathUtils.lerp(config.durationMin, config.durationMax, percentageDamage)
            }

            DamageShockMode.DamageAmount -> {
                val percentageDamage = (damage / player.maxHealth).coerceAtLeast(0f).coerceAtMost(1f)
                if (percentageDamage < percentageThreshold) {
                    logger.debug("Damage percentage is below threshold")
                    return
                }

                intensity = MathUtils.lerp(config.intensityMin, config.intensityMax, percentageDamage)
                duration = MathUtils.lerp(config.durationMin, config.durationMax, percentageDamage)
            }
        }

        OpenShockApi.control(
            ControlType.Shock,
            intensity,
            duration,
            getName(player.recentDamageSource)
        )
    }

    private fun getName(damageSource: DamageSource?): String {
        if (damageSource != null) {
            return if (damageSource.attacker != null && damageSource.attacker!!.name.literalString != null) damageSource.attacker!!.name.literalString!!
            else damageSource.name
        }
        return "Unknown"
    }

}