package daio7771.dioriteisntuseless.registry

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.recipe.CountedCookingSerializer
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.crafting.BlastingRecipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.SmeltingRecipe

object ModRecipes {

    /** `"type": "dioriteisntuseless:smelting"`: like minecraft:smelting, but with a count in the result. */
    val SMELTING: RecipeSerializer<SmeltingRecipe> =
        register("smelting", CountedCookingSerializer(::SmeltingRecipe, 200))

    /** `"type": "dioriteisntuseless:blasting"`: like minecraft:blasting, but with a count in the result. */
    val BLASTING: RecipeSerializer<BlastingRecipe> =
        register("blasting", CountedCookingSerializer(::BlastingRecipe, 100))

    private fun <T : RecipeSerializer<*>> register(name: String, serializer: T): T =
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Dioriteisntuseless.id(name), serializer)

    /** Call from onInitialize: accessing the object registers the serializers above. */
    fun init() = Unit
}
