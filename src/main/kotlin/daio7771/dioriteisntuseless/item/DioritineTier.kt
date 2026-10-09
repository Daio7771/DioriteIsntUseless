package daio7771.dioriteisntuseless.item

import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.world.item.Tier
import net.minecraft.world.item.crafting.Ingredient

/** Material of the dioritine tools (axe and pickaxe). */
object DioritineTier : Tier {

    // Lazy: the ingot item is registered after this class is loaded.
    private val repairWith by lazy { Ingredient.of(ModItems.DIORITINE_INGOT) }

    /** axe.durability as it was at startup: the axe and the pickaxe fix it when they are registered. */
    override fun getUses(): Int = ModConfig.axeDurabilityAtStartup

    /** Speed on the blocks the tool is effective on (like iron). */
    override fun getSpeed(): Float = 6.0f

    /** Each tool's damage is set entirely in its class (DioritineAxeItem, DioritinePickaxeItem). */
    override fun getAttackDamageBonus(): Float = 0f

    /** Iron level: the pickaxe can mine diamond and emerald. No vanilla axe block requires a level. */
    override fun getLevel(): Int = 2

    override fun getEnchantmentValue(): Int = 14

    override fun getRepairIngredient(): Ingredient = repairWith
}
