package daio7771.dioriteisntuseless.item

import net.minecraft.stats.Stats
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.min

/**
 * What the dioritine axe and pickaxe share: they break on reaching a number of uses (trees,
 * strikes), whatever durability they have left, and their bar shows whatever is closer to
 * breaking them.
 */
internal object ToolWear {

    /**
     * Breaks [stack], the tool in the main hand of [player]: the same thing vanilla does when a
     * tool runs out of durability (sound, particles and the "item broken" statistic, which the
     * advancements come from). The stack ends up empty.
     */
    fun breakInMainHand(stack: ItemStack, player: Player) {
        player.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        val item = stack.item
        stack.shrink(1)
        player.awardStat(Stats.ITEM_BROKEN.get(item))
        stack.damageValue = 0
    }

    /** Fraction (0 to 1) of the [limit] uses it has left; 1 if there is no limit. */
    fun remainingUses(used: Int, limit: Int): Float =
        if (limit <= 0) 1f else ((limit - used).toFloat() / limit).coerceIn(0f, 1f)

    /** For the bar: what it has left, by durability or by uses ([usesLeft]), whichever is less. */
    fun remainingFraction(stack: ItemStack, usesLeft: Float): Float {
        val durability = (stack.maxDamage - stack.damageValue).toFloat() / stack.maxDamage
        return min(durability, usesLeft).coerceIn(0f, 1f)
    }

    fun barWidth(remaining: Float): Int = Math.round(remaining * 13f)

    fun barColor(remaining: Float): Int = Mth.hsvToRgb(remaining / 3f, 1f, 1f)
}
