package daio7771.dioriteisntuseless.recipe

import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.CookingBookCategory
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.SimpleCookingSerializer

/**
 * Igual que el serializador vanilla de cocción, pero "result" es un objeto
 * `{"item": ..., "count": N}`. En 1.20.1 el vanilla solo admite el ID del ítem (siempre 1).
 *
 * Solo cambia la lectura del JSON: las recetas creadas son SmeltingRecipe/BlastingRecipe
 * vanilla, cuyo getSerializer() es el vanilla. Así la sincronización con el cliente (que ya
 * transmite el count) usa el código de Minecraft, y hornos, tolvas, libro de recetas y mods
 * como REI/JEI/EMI las tratan como fundición normal.
 *
 * Para que el horno entregue el count completo también cuando la salida ya tiene ítems,
 * hace falta además AbstractFurnaceBlockEntityMixin.
 */
class CountedCookingSerializer<T : AbstractCookingRecipe>(
    private val factory: CookieBaker<T>,
    private val defaultCookingTime: Int,
) : SimpleCookingSerializer<T>(factory, defaultCookingTime) {

    override fun fromJson(id: ResourceLocation, json: JsonObject): T {
        val group = GsonHelper.getAsString(json, "group", "") ?: ""
        val category = CookingBookCategory.CODEC.byName(
            GsonHelper.getAsString(json, "category", null),
            CookingBookCategory.MISC,
        )
        val ingredientJson =
            if (GsonHelper.isArrayNode(json, "ingredient")) GsonHelper.getAsJsonArray(json, "ingredient")
            else GsonHelper.getAsJsonObject(json, "ingredient")
        val ingredient = Ingredient.fromJson(ingredientJson, false)
        // Mismo formato que el resultado de las recetas de mesa: {"item": ..., "count": N}.
        val result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"))
        if (result.count > result.maxStackSize) {
            throw JsonSyntaxException("Result count ${result.count} exceeds max stack size ${result.maxStackSize}")
        }
        val experience = GsonHelper.getAsFloat(json, "experience", 0f)
        val cookingTime = GsonHelper.getAsInt(json, "cookingtime", defaultCookingTime)
        return factory.create(id, group, category, ingredient, result, experience, cookingTime)
    }
}
