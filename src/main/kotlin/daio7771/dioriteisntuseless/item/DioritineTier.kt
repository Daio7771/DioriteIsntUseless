package daio7771.dioriteisntuseless.item

import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.world.item.Tier
import net.minecraft.world.item.crafting.Ingredient

/** Material de las herramientas de dioritina. */
object DioritineTier : Tier {

    // Perezoso: el ítem del lingote se registra después de cargar esta clase.
    private val repairWith by lazy { Ingredient.of(ModItems.DIORITINE_INGOT) }

    /** axe.durability tal como estaba al arrancar: el hacha la fija al registrarse. */
    override fun getUses(): Int = ModConfig.axeDurabilityAtStartup

    /** Velocidad en los bloques en los que la herramienta es eficaz (como el hierro). */
    override fun getSpeed(): Float = 6.0f

    /** El daño del hacha se fija entero en DioritineAxeItem. */
    override fun getAttackDamageBonus(): Float = 0f

    /** Nivel de hierro. Solo afecta a bloques que exijan nivel de herramienta; ningún bloque vanilla de hacha lo hace. */
    override fun getLevel(): Int = 2

    override fun getEnchantmentValue(): Int = 14

    override fun getRepairIngredient(): Ingredient = repairWith
}
