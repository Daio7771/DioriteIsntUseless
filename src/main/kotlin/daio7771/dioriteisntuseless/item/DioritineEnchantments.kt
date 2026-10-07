package daio7771.dioriteisntuseless.item

import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments

/**
 * Encantamientos permitidos en el Dioritine Axe: solo Efficiency y Mending, por cualquier vía.
 * La consultan los mixins de la mesa de encantar, el yunque y el comando /enchant.
 * El resto de ítems no se ven afectados. Con axe.restrictEnchantments = false, el hacha acepta
 * los mismos encantamientos que un hacha vanilla.
 */
object DioritineEnchantments {

    private val ALLOWED: Set<Enchantment> by lazy { setOf(Enchantments.BLOCK_EFFICIENCY, Enchantments.MENDING) }

    /** true si el ítem tiene la lista de encantamientos restringida. */
    @JvmStatic
    fun isRestricted(stack: ItemStack): Boolean =
        stack.`is`(ModItems.DIORITINE_AXE) && ModConfig.current.axe.restrictEnchantments

    @JvmStatic
    fun isAllowed(stack: ItemStack, enchantment: Enchantment): Boolean =
        !isRestricted(stack) || enchantment in ALLOWED
}
