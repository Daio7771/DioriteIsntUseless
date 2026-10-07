package daio7771.dioriteisntuseless.registry

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.recipe.CountedCookingSerializer
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.crafting.BlastingRecipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.SmeltingRecipe

object ModRecipes {

    /** `"type": "dioriteisntuseless:smelting"`: como minecraft:smelting, pero con count en el resultado. */
    val SMELTING: RecipeSerializer<SmeltingRecipe> =
        register("smelting", CountedCookingSerializer(::SmeltingRecipe, 200))

    /** `"type": "dioriteisntuseless:blasting"`: como minecraft:blasting, pero con count en el resultado. */
    val BLASTING: RecipeSerializer<BlastingRecipe> =
        register("blasting", CountedCookingSerializer(::BlastingRecipe, 100))

    private fun <T : RecipeSerializer<*>> register(name: String, serializer: T): T =
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Dioriteisntuseless.id(name), serializer)

    /** Llamar desde onInitialize: acceder al objeto registra los serializadores de arriba. */
    fun init() = Unit
}
