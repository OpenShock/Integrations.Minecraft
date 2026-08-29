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
import openshock.integrations.minecraft.config.AccountConfig
import openshock.integrations.minecraft.config.DamageFilter
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

        // Re-saving after a migration rewrites the instance config without the credential fields,
        // so the token stops living in the file that gets shared with modpacks and bug reports.
        if (AccountConfig.loadOrMigrate()) ShockCraftConfig.HANDLER.save()
    }

    var lastTickHealth: Float = 20f
    var lastTickReset: Boolean = false
    var pauseMenuOpen: Boolean = true

    var lastTickXpLevel: Int = 0

    /**
     * What a shock is sent under: who hurt us and how, e.g. `Zombie (mob_attack)`. It becomes the
     * OpenShock control name and, with the action bar switched on, what you read on screen.
     *
     * Both halves earn their place - "Zombie" alone does not say whether that was a hit or its
     * arrow, and a bare type does not say who threw it. The type is spelled the way the config
     * screen lists it, so a name you see here can be found in the Exact Damage Types picker.
     *
     * The server only sends a causing/direct entity when something actually attacked you, so
     * environmental damage (fall, lava, drowning, fire, cactus, ...) has neither and is left as
     * the type on its own.
     */
    val DamageSource?.controlName: String
        get() {
            if (this == null) return "Unknown"

            // getEntity() is the mob/player behind it, getDirectEntity() the projectile it used.
            val attacker = (this.entity ?: this.directEntity)?.name?.string

            // msgId is the fallback for a hit whose type never reached the client: it is what
            // vanilla names the death message after, so it still reads like something.
            val type = DamageFilter.typeIdOf(this)?.let(DamageFilter::shortName) ?: this.msgId

            return if (attacker == null) type else "$attacker ($type)"
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
            // The exact id rather than the message id, so the log names damage the same way the
            // Exact Damage Types picker does and can be copied straight into the manual list.
            logger.debug("{} - {}", DamageFilter.describe(player.lastDamageSource), damageSinceLastTick)

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
            player.lastDamageSource.controlName,
        )
    }

    private var lastShock: Long = -1

    private suspend fun onDamage(player: LocalPlayer, damage: Float) {
        val config = ShockCraftConfig.HANDLER.instance()
        if (!config.onDamage) return

        // Ahead of the cooldown on purpose: a hit that is filtered out must not take the cooldown
        // with it and swallow the next one that would have counted.
        if (!DamageFilter.allows(config, player.lastDamageSource)) {
            logger.debug("Ignoring {} damage, it is switched off", DamageFilter.describe(player.lastDamageSource))
            return
        }

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
            player.lastDamageSource.controlName,
        )
    }
}
