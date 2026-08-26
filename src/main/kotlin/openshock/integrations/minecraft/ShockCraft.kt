package openshock.integrations.minecraft

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.damagesource.DamageSource
import openshock.integrations.minecraft.api.ControlType
import openshock.integrations.minecraft.api.OpenShockApi
import openshock.integrations.minecraft.config.DamageShockMode
import openshock.integrations.minecraft.config.ShockCraftConfig
import openshock.integrations.minecraft.platform.McCompat
import openshock.integrations.minecraft.utils.MathUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Loader-agnostic core of the mod. Everything here compiles against plain Minecraft classes only,
 * so it is shared verbatim by every Minecraft version and both loaders. The Fabric and NeoForge
 * entrypoints in [openshock.integrations.minecraft.platform] are the only things that differ.
 */
object ShockCraft {
    const val MOD_ID: String = "shockcraft"

    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    fun init() {
        logger.info("ShockCraft starting up")
        ShockCraftConfig.HANDLER.load()
    }

    var lastTickHealth: Float = 20f
    var lastTickReset: Boolean = false
    var pauseMenuOpen: Boolean = true

    var lastTickXpLevel: Int = 0

    /**
     * A human-readable label for what hurt us, used as the OpenShock control name.
     *
     * The server only sends a causing/direct entity when something actually attacked you, so
     * environmental damage (fall, lava, drowning, fire, cactus, ...) has neither. In that case fall
     * back to the damage type id, which is what vanilla names the death message after.
     */
    val DamageSource?.attackerName: String
        get() {
            if (this == null) return "Unknown"
            // getEntity() is the mob/player behind it, getDirectEntity() the projectile it used.
            val attacker = this.entity ?: this.directEntity
            return attacker?.name?.string ?: this.msgId
        }

    private fun reset() {
        lastTickReset = true
        val player = Minecraft.getInstance().player

        if (player == null) {
            lastTickHealth = 20f
            lastTickXpLevel = 0
            return
        }

        lastTickHealth = player.maxHealth
        lastTickXpLevel = player.experienceLevel
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun onClientTick() {
        val currentScreen = McCompat.currentScreen

        // Cursed if logic to see if pause menu was opened, might not work with all mods
        if (currentScreen != null) {
            if (!pauseMenuOpen && currentScreen is PauseScreen) {
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

        val player = Minecraft.getInstance().player

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
            lastTickXpLevel = player.experienceLevel
        }

        val damageSinceLastTick = (lastTickHealth - player.health).coerceAtLeast(0f)
        val xpLevelChange = player.experienceLevel - lastTickXpLevel

        // Set last tick health and experience level, we already calculated what we need
        lastTickHealth = player.health
        lastTickXpLevel = player.experienceLevel

        // Did we take damage?
        if (damageSinceLastTick > 0) {
            logger.debug(player.lastDamageSource?.msgId + " - " + damageSinceLastTick.toString())

            if (player.isDeadOrDying) {
                logger.debug("Player died")
                GlobalScope.launch {
                    onDeath(player)
                }
                return
            }

            GlobalScope.launch { onDamage(player, damageSinceLastTick) }
        }

        if (xpLevelChange > 0) {
            logger.debug("Player leveled up by $xpLevelChange levels")
            GlobalScope.launch {
                onLevelUp(xpLevelChange)
            }
        }
    }

    private suspend fun onLevelUp(levels: Int) {
        val config = ShockCraftConfig.HANDLER.instance()
        if (!config.onLevelUp) return

        OpenShockApi.control(
            ControlType.Shock,
            config.onLevelUpIntensity,
            config.onLevelUpDuration,
            "XP Level Up ($levels)",
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun onChatMessage(message: String) {
        val config = ShockCraftConfig.HANDLER.instance()
        if (!config.onChatEvent) return
        if (config.chatMessagePhrase.isBlank()) return

        if (message.contains(config.chatMessagePhrase, ignoreCase = true)) {
            logger.debug("Chat message triggered shock: $message")
            GlobalScope.launch {
                OpenShockApi.control(
                    ControlType.Shock,
                    config.onChatMessageIntensity,
                    config.onChatMessageDuration,
                    "Chat Message Event",
                )
            }
        }
    }

    private suspend fun onDeath(player: LocalPlayer) {
        val config = ShockCraftConfig.HANDLER.instance()
        if (!config.onDeath) return

        OpenShockApi.control(
            ControlType.Shock,
            config.onDeathIntensity,
            config.onDeathDuration,
            player.lastDamageSource.attackerName,
        )
    }

    private var lastShock: Long = -1

    private suspend fun onDamage(player: LocalPlayer, damage: Float) {
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
            player.lastDamageSource.attackerName,
        )
    }
}
