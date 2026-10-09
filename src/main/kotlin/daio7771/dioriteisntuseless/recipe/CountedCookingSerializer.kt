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
 * Same as the vanilla cooking serializer, but "result" is an object `{"item": ..., "count": N}`.
 * In 1.20.1 the vanilla one only accepts the item ID (always 1).
 *
 * Only the JSON reading changes: the recipes created are vanilla SmeltingRecipe/BlastingRecipe,
 * whose getSerializer() is the vanilla one. That way the sync with the client (which already
 * sends the count) uses Minecraft's own code, and furnaces, hoppers, the recipe book and mods
 * like REI/JEI/EMI treat them as normal smelting.
 *
 * For the furnace to deliver the full count also when the output already has items,
 * AbstractFurnaceBlockEntityMixin is needed as well.
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
        // Same format as the result of crafting table recipes: {"item": ..., "count": N}.
        val result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"))
        if (result.count > result.maxStackSize) {
            throw JsonSyntaxException("Result count ${result.count} exceeds max stack size ${result.maxStackSize}")
        }
        val experience = GsonHelper.getAsFloat(json, "experience", 0f)
        val cookingTime = GsonHelper.getAsInt(json, "cookingtime", defaultCookingTime)
        return factory.create(id, group, category, ingredient, result, experience, cookingTime)
    }
}
