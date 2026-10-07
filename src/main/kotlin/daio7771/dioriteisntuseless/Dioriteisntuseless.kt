package daio7771.dioriteisntuseless

import daio7771.dioriteisntuseless.ability.TreeFeller
import daio7771.dioriteisntuseless.registry.ModItems
import daio7771.dioriteisntuseless.registry.ModRecipes
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.ResourceLocation

class Dioriteisntuseless : ModInitializer {

    override fun onInitialize() {
        ModItems.init()
        ModRecipes.init()
        TreeFeller.init()
    }

    companion object {
        /** Debe coincidir con el "id" de fabric.mod.json. */
        const val MOD_ID = "dioriteisntuseless"

        fun id(path: String): ResourceLocation = ResourceLocation(MOD_ID, path)
    }
}
