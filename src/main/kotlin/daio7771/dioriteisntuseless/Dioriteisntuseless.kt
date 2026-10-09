package daio7771.dioriteisntuseless

import daio7771.dioriteisntuseless.ability.AreaMiner
import daio7771.dioriteisntuseless.ability.TreeFeller
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.command.DiuCommand
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.network.ConfigSync
import daio7771.dioriteisntuseless.registry.ModItems
import daio7771.dioriteisntuseless.registry.ModRecipes
import daio7771.dioriteisntuseless.registry.ModSounds
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Dioriteisntuseless : ModInitializer {

    override fun onInitialize() {
        // First of all: the durability of the axe and the pickaxe comes from the config when they are registered.
        ModConfig.loadAtStartup()
        ModItems.init()
        ModSounds.init()
        ModRecipes.init()
        TreeFeller.init()
        AreaMiner.init()
        DiuCommand.init()
        ConfigSync.init()
        AbuseTracker.init()
    }

    companion object {
        /** Must match the "id" in fabric.mod.json. */
        const val MOD_ID = "dioriteisntuseless"

        val LOGGER: Logger = LoggerFactory.getLogger("DioriteIsntUseless")

        fun id(path: String): ResourceLocation = ResourceLocation(MOD_ID, path)
    }
}
