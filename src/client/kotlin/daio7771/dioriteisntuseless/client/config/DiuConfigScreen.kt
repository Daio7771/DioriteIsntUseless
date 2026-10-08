package daio7771.dioriteisntuseless.client.config

import daio7771.dioriteisntuseless.block.DioriteStats
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.config.SneakMode
import daio7771.dioriteisntuseless.network.ConfigSync
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Pantalla de configuración con Cloth Config. Es la única clase que usa Cloth Config: solo se
 * carga si Cloth Config está instalado (lo comprueba ModMenuIntegration).
 */
object DiuConfigScreen {

    private const val LANG = "config.dioriteisntuseless"

    fun create(parent: Screen?): Screen {
        val current = ModConfig.current
        val defaults = DiuConfig()
        // Cloth Config llama a los setSaveConsumer al guardar y después a setSavingRunnable.
        var diorite = current.diorite
        var treeFelling = current.treeFelling
        var axe = current.axe
        var abuseMode = current.abuseMode

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(text("title"))
        val entries = builder.entryBuilder()

        builder.getOrCreateCategory(text("category.diorite")).apply {
            if (DioriteStats.usingServerValues) {
                addEntry(entries.startTextDescription(text("diorite.serverValues").withStyle(ChatFormatting.YELLOW)).build())
            }
            addEntry(
                entries.startBooleanToggle(text("diorite.enabled"), current.diorite.enabled)
                    .setDefaultValue(defaults.diorite.enabled)
                    .setTooltip(text("diorite.enabled.tooltip"))
                    .setSaveConsumer { diorite = diorite.copy(enabled = it) }
                    .build()
            )
            addEntry(
                entries.startFloatField(text("diorite.hardness"), current.diorite.hardness)
                    .setDefaultValue(defaults.diorite.hardness)
                    .setMin(DiuConfig.Limits.HARDNESS.start)
                    .setMax(DiuConfig.Limits.HARDNESS.endInclusive)
                    .setTooltip(text("diorite.hardness.tooltip"))
                    .setSaveConsumer { diorite = diorite.copy(hardness = it) }
                    .build()
            )
            addEntry(
                entries.startFloatField(text("diorite.blastResistance"), current.diorite.blastResistance)
                    .setDefaultValue(defaults.diorite.blastResistance)
                    .setMin(DiuConfig.Limits.BLAST_RESISTANCE.start)
                    .setMax(DiuConfig.Limits.BLAST_RESISTANCE.endInclusive)
                    .setTooltip(text("diorite.blastResistance.tooltip"))
                    .setSaveConsumer { diorite = diorite.copy(blastResistance = it) }
                    .build()
            )
        }

        builder.getOrCreateCategory(text("category.treeFelling")).apply {
            addEntry(
                entries.startBooleanToggle(text("treeFelling.enabled"), current.treeFelling.enabled)
                    .setDefaultValue(defaults.treeFelling.enabled)
                    .setTooltip(text("treeFelling.enabled.tooltip"))
                    .setSaveConsumer { treeFelling = treeFelling.copy(enabled = it) }
                    .build()
            )
            addEntry(
                entries.startIntSlider(
                    text("treeFelling.maxLogs"), current.treeFelling.maxLogs,
                    DiuConfig.Limits.MAX_LOGS.first, DiuConfig.Limits.MAX_LOGS.last,
                )
                    .setDefaultValue(defaults.treeFelling.maxLogs)
                    .setTooltip(text("treeFelling.maxLogs.tooltip"))
                    .setSaveConsumer { treeFelling = treeFelling.copy(maxLogs = it) }
                    .build()
            )
            addEntry(
                entries.startIntSlider(
                    text("treeFelling.logsPerDurabilityPoint"), current.treeFelling.logsPerDurabilityPoint,
                    DiuConfig.Limits.LOGS_PER_DURABILITY_POINT.first, DiuConfig.Limits.LOGS_PER_DURABILITY_POINT.last,
                )
                    .setDefaultValue(defaults.treeFelling.logsPerDurabilityPoint)
                    .setTooltip(text("treeFelling.logsPerDurabilityPoint.tooltip"))
                    .setSaveConsumer { treeFelling = treeFelling.copy(logsPerDurabilityPoint = it) }
                    .build()
            )
            addEntry(
                entries.startBooleanToggle(text("treeFelling.dropsAtOrigin"), current.treeFelling.dropsAtOrigin)
                    .setDefaultValue(defaults.treeFelling.dropsAtOrigin)
                    .setTooltip(text("treeFelling.dropsAtOrigin.tooltip"))
                    .setSaveConsumer { treeFelling = treeFelling.copy(dropsAtOrigin = it) }
                    .build()
            )
            addEntry(
                entries.startEnumSelector(text("treeFelling.sneakMode"), SneakMode::class.java, current.treeFelling.sneakMode)
                    .setDefaultValue(defaults.treeFelling.sneakMode)
                    .setEnumNameProvider { text("treeFelling.sneakMode.${it.name}") }
                    .setTooltip(text("treeFelling.sneakMode.tooltip"))
                    .setSaveConsumer { treeFelling = treeFelling.copy(sneakMode = it) }
                    .build()
            )
        }

        builder.getOrCreateCategory(text("category.axe")).apply {
            addEntry(
                entries.startIntField(text("axe.durability"), current.axe.durability)
                    .setDefaultValue(defaults.axe.durability)
                    .setMin(DiuConfig.Limits.AXE_DURABILITY.first)
                    .setMax(DiuConfig.Limits.AXE_DURABILITY.last)
                    .setTooltip(text("axe.durability.tooltip"))
                    .requireRestart()
                    .setSaveConsumer { axe = axe.copy(durability = it) }
                    .build()
            )
            addEntry(
                entries.startBooleanToggle(text("axe.restrictEnchantments"), current.axe.restrictEnchantments)
                    .setDefaultValue(defaults.axe.restrictEnchantments)
                    .setTooltip(text("axe.restrictEnchantments.tooltip"))
                    .setSaveConsumer { axe = axe.copy(restrictEnchantments = it) }
                    .build()
            )
        }

        builder.getOrCreateCategory(text("category.abuseMode")).apply {
            addEntry(
                entries.startBooleanToggle(text("abuseMode.enabled"), current.abuseMode.enabled)
                    .setDefaultValue(defaults.abuseMode.enabled)
                    .setTooltip(text("abuseMode.enabled.tooltip"))
                    .setSaveConsumer { abuseMode = abuseMode.copy(enabled = it) }
                    .build()
            )
            // Umbrales y tiempos: discretos, en una subsección plegada.
            val advanced = entries.startSubCategory(text("abuseMode.advanced")).setExpanded(false)
            for (i in 0 until DiuConfig.AbuseMode.LEVELS) {
                advanced += entries.startIntField(text("abuseMode.threshold", i + 1), current.abuseMode.levelThresholds[i])
                    .setDefaultValue(defaults.abuseMode.levelThresholds[i])
                    .setMin(DiuConfig.Limits.ABUSE_THRESHOLD.first)
                    .setMax(DiuConfig.Limits.ABUSE_THRESHOLD.last)
                    .setSaveConsumer { value ->
                        abuseMode = abuseMode.copy(levelThresholds = abuseMode.levelThresholds.with(i, value))
                    }
                    .build()
            }
            for (i in 0 until DiuConfig.AbuseMode.LEVELS) {
                advanced += entries.startIntField(text("abuseMode.minDays", i + 1), current.abuseMode.minDaysBetweenLevels[i])
                    .setDefaultValue(defaults.abuseMode.minDaysBetweenLevels[i])
                    .setMin(DiuConfig.Limits.ABUSE_DAYS.first)
                    .setMax(DiuConfig.Limits.ABUSE_DAYS.last)
                    .setSaveConsumer { value ->
                        abuseMode = abuseMode.copy(minDaysBetweenLevels = abuseMode.minDaysBetweenLevels.with(i, value))
                    }
                    .build()
            }
            advanced += entries.startIntField(text("abuseMode.daysUntilEnding"), current.abuseMode.daysUntilEnding)
                .setDefaultValue(defaults.abuseMode.daysUntilEnding)
                .setMin(DiuConfig.Limits.ABUSE_DAYS.first)
                .setMax(DiuConfig.Limits.ABUSE_DAYS.last)
                .setSaveConsumer { abuseMode = abuseMode.copy(daysUntilEnding = it) }
                .build()
            addEntry(advanced.build())
        }

        builder.setSavingRunnable {
            // Se parte de la configuración en vigor para no perder lo que no sale en la pantalla.
            save(ModConfig.current.copy(diorite = diorite, treeFelling = treeFelling, axe = axe, abuseMode = abuseMode))
        }
        return builder.build()
    }

    private fun List<Int>.with(index: Int, value: Int): List<Int> = toMutableList().also { it[index] = value }

    /** Escribe el JSON, aplica los cambios en caliente y, si hay partida LAN, avisa a los demás. */
    private fun save(config: DiuConfig) {
        ModConfig.save(config)
        val server = Minecraft.getInstance().singleplayerServer ?: return
        server.execute { ConfigSync.broadcast(server) }
    }

    private fun text(key: String, vararg args: Any) = Component.translatable("$LANG.$key", *args)
}
