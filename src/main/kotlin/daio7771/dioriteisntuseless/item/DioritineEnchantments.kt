package daio7771.dioriteisntuseless.item

import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments

/**
 * Enchantments allowed on the dioritine axe and pickaxe: only Efficiency and Mending, through any
 * route. Checked by the mixins of the enchanting table, the anvil and the /enchant command.
 * Other items are not affected. With axe.restrictEnchantments = false, they accept the same
 * enchantments as a vanilla axe or pickaxe.
 */
object DioritineEnchantments {

    private val ALLOWED: Set<Enchantment> by lazy { setOf(Enchantments.BLOCK_EFFICIENCY, Enchantments.MENDING) }

    /** true if the item has its list of enchantments restricted. */
    @JvmStatic
    fun isRestricted(stack: ItemStack): Boolean =
        (stack.`is`(ModItems.DIORITINE_AXE) || stack.`is`(ModItems.DIORITINE_PICKAXE)) && ModConfig.current.axe.restrictEnchantments

    @JvmStatic
    fun isAllowed(stack: ItemStack, enchantment: Enchantment): Boolean =
        !isRestricted(stack) || enchantment in ALLOWED
}
