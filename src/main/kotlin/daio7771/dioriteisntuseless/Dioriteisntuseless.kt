package daio7771.dioriteisntuseless

import daio7771.dioriteisntuseless.ability.TreeFeller
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.command.DiuCommand
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.network.ConfigSync
import daio7771.dioriteisntuseless.registry.ModItems
import daio7771.dioriteisntuseless.registry.ModRecipes
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Dioriteisntuseless : ModInitializer {

    override fun onInitialize() {
        // Lo primero: la durabilidad del hacha sale de la configuración al registrar el ítem.
        ModConfig.loadAtStartup()
        ModItems.init()
        ModRecipes.init()
        TreeFeller.init()
        DiuCommand.init()
        ConfigSync.init()
        AbuseTracker.init()
    }

    companion object {
        /** Debe coincidir con el "id" de fabric.mod.json. */
        const val MOD_ID = "dioriteisntuseless"

        val LOGGER: Logger = LoggerFactory.getLogger("DioriteIsntUseless")

        fun id(path: String): ResourceLocation = ResourceLocation(MOD_ID, path)
    }
}
