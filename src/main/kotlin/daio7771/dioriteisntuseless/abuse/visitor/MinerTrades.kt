package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers

/**
 * What The Miner buys: Diorite Crystals (not ingots) for emeralds. Available from his very first
 * visit; the abuse level only makes him pay better, which is not a good sign.
 */
object MinerTrades {

    /** Crystals per emerald at abuse levels 0, 1, 2 and 3 (and above). */
    private val CRYSTALS_PER_EMERALD = intArrayOf(24, 20, 16, 12)

    fun crystalsPerEmerald(level: Int): Int = CRYSTALS_PER_EMERALD[level.coerceIn(0, CRYSTALS_PER_EMERALD.lastIndex)]

    /** His offers for a player at abuse [level]: built again every time they talk to him. No limit of uses and no XP. */
    fun offersFor(level: Int): MerchantOffers = MerchantOffers().apply {
        add(MerchantOffer(ItemStack(ModItems.DIORITE_CRYSTAL, crystalsPerEmerald(level)), ItemStack(Items.EMERALD), Int.MAX_VALUE, 0, 0f))
    }
}
