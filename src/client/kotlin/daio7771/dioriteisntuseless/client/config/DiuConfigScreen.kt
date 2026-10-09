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
 * Config screen built with Cloth Config. It is the only class that uses Cloth Config: it is only
 * loaded if Cloth Config is installed (ModMenuIntegration checks it).
 */
object DiuConfigScreen {

    private const val LANG = "config.dioriteisntuseless"

    fun create(parent: Screen?): Screen {
        val current = ModConfig.current
        val defaults = DiuConfig()
        // Cloth Config calls the setSaveConsumer callbacks on saving and then setSavingRunnable.
        var diorite = current.diorite
        var treeFelling = current.treeFelling
        var axe = current.axe
        var pickaxe = current.pickaxe
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
            addEntry(
                entries.startIntField(text("treeFelling.treesBeforeBreaking"), current.treeFelling.treesBeforeBreaking)
                    .setDefaultValue(defaults.treeFelling.treesBeforeBreaking)
                    .setMin(DiuConfig.Limits.TREES_BEFORE_BREAKING.first)
                    .setMax(DiuConfig.Limits.TREES_BEFORE_BREAKING.last)
                    .setTooltip(text("treeFelling.treesBeforeBreaking.tooltip"))
                    .setSaveConsumer { treeFelling = treeFelling.copy(treesBeforeBreaking = it) }
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

        builder.getOrCreateCategory(text("category.pickaxe")).apply {
            addEntry(
                entries.startBooleanToggle(text("pickaxe.enabled"), current.pickaxe.enabled)
                    .setDefaultValue(defaults.pickaxe.enabled)
                    .setTooltip(text("pickaxe.enabled.tooltip"))
                    .setSaveConsumer { pickaxe = pickaxe.copy(enabled = it) }
                    .build()
            )
            addEntry(
                entries.startBooleanToggle(text("pickaxe.dropsAtOrigin"), current.pickaxe.dropsAtOrigin)
                    .setDefaultValue(defaults.pickaxe.dropsAtOrigin)
                    .setTooltip(text("pickaxe.dropsAtOrigin.tooltip"))
                    .setSaveConsumer { pickaxe = pickaxe.copy(dropsAtOrigin = it) }
                    .build()
            )
            addEntry(
                entries.startEnumSelector(text("pickaxe.sneakMode"), SneakMode::class.java, current.pickaxe.sneakMode)
                    .setDefaultValue(defaults.pickaxe.sneakMode)
                    .setEnumNameProvider { text("pickaxe.sneakMode.${it.name}") }
                    .setTooltip(text("pickaxe.sneakMode.tooltip"))
                    .setSaveConsumer { pickaxe = pickaxe.copy(sneakMode = it) }
                    .build()
            )
            addEntry(
                entries.startIntField(text("pickaxe.strikesBeforeBreaking"), current.pickaxe.strikesBeforeBreaking)
                    .setDefaultValue(defaults.pickaxe.strikesBeforeBreaking)
                    .setMin(DiuConfig.Limits.STRIKES_BEFORE_BREAKING.first)
                    .setMax(DiuConfig.Limits.STRIKES_BEFORE_BREAKING.last)
                    .setTooltip(text("pickaxe.strikesBeforeBreaking.tooltip"))
                    .setSaveConsumer { pickaxe = pickaxe.copy(strikesBeforeBreaking = it) }
                    .build()
            )
            addEntry(
                entries.startIntSlider(
                    text("pickaxe.minBlocksForStrike"), current.pickaxe.minBlocksForStrike,
                    DiuConfig.Limits.MIN_BLOCKS_FOR_STRIKE.first, DiuConfig.Limits.MIN_BLOCKS_FOR_STRIKE.last,
                )
                    .setDefaultValue(defaults.pickaxe.minBlocksForStrike)
                    .setTooltip(text("pickaxe.minBlocksForStrike.tooltip"))
                    .setSaveConsumer { pickaxe = pickaxe.copy(minBlocksForStrike = it) }
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
            // Trees and strikes per level: tucked away, in a collapsed subsection.
            val advanced = entries.startSubCategory(text("abuseMode.advanced")).setExpanded(false)
            for (i in 0 until DiuConfig.AbuseMode.LEVELS) {
                advanced += entries.startIntField(text("abuseMode.treesPerLevel", i + 1), current.abuseMode.treesPerLevel[i])
                    .setDefaultValue(defaults.abuseMode.treesPerLevel[i])
                    .setMin(DiuConfig.Limits.ABUSE_TREES.first)
                    .setMax(DiuConfig.Limits.ABUSE_TREES.last)
                    .setTooltip(text("abuseMode.treesPerLevel.tooltip"))
                    .setSaveConsumer { value ->
                        abuseMode = abuseMode.copy(treesPerLevel = abuseMode.treesPerLevel.with(i, value))
                    }
                    .build()
            }
            advanced += entries.startIntField(text("abuseMode.treesUntilEnding"), current.abuseMode.treesUntilEnding)
                .setDefaultValue(defaults.abuseMode.treesUntilEnding)
                .setMin(DiuConfig.Limits.ABUSE_TREES.first)
                .setMax(DiuConfig.Limits.ABUSE_TREES.last)
                .setTooltip(text("abuseMode.treesUntilEnding.tooltip"))
                .setSaveConsumer { abuseMode = abuseMode.copy(treesUntilEnding = it) }
                .build()
            for (i in 0 until DiuConfig.AbuseMode.STEPS) {
                // The last number is the ending's.
                val label = if (i < DiuConfig.AbuseMode.LEVELS) {
                    text("abuseMode.pickaxeSteps", i + 1)
                } else {
                    text("abuseMode.pickaxeStepsFinal")
                }
                advanced += entries.startIntField(label, current.abuseMode.pickaxeSteps[i])
                    .setDefaultValue(defaults.abuseMode.pickaxeSteps[i])
                    .setMin(DiuConfig.Limits.ABUSE_STRIKES.first)
                    .setMax(DiuConfig.Limits.ABUSE_STRIKES.last)
                    .setTooltip(text("abuseMode.pickaxeSteps.tooltip"))
                    .setSaveConsumer { value ->
                        abuseMode = abuseMode.copy(pickaxeSteps = abuseMode.pickaxeSteps.with(i, value))
                    }
                    .build()
            }
            addEntry(advanced.build())
        }

        builder.setSavingRunnable {
            // Start from the config in effect so nothing that is not on the screen is lost.
            save(
                ModConfig.current.copy(
                    diorite = diorite, treeFelling = treeFelling, axe = axe, pickaxe = pickaxe, abuseMode = abuseMode,
                )
            )
        }
        return builder.build()
    }

    private fun List<Int>.with(index: Int, value: Int): List<Int> = toMutableList().also { it[index] = value }

    /** Writes the JSON, applies the changes right away and, if there is a LAN game, tells the others. */
    private fun save(config: DiuConfig) {
        ModConfig.save(config)
        val server = Minecraft.getInstance().singleplayerServer ?: return
        server.execute { ConfigSync.broadcast(server) }
    }

    private fun text(key: String, vararg args: Any) = Component.translatable("$LANG.$key", *args)
}
