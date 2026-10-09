package daio7771.dioriteisntuseless.item

import net.minecraft.stats.Stats
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.min

/**
 * Lo que comparten el hacha y el pico de dioritina: se rompen al llegar a un número de usos
 * (árboles, picadas), les quede la durabilidad que les quede, y su barra muestra lo que esté más
 * cerca de romperlos.
 */
internal object ToolWear {

    /**
     * Rompe [stack], la herramienta de la mano principal de [player]: lo mismo que hace vanilla
     * cuando una herramienta se queda sin durabilidad (sonido, partículas y la estadística
     * "objeto roto", de la que salen los logros). La pila queda vacía.
     */
    fun breakInMainHand(stack: ItemStack, player: Player) {
        player.broadcastBreakEvent(EquipmentSlot.MAINHAND)
        val item = stack.item
        stack.shrink(1)
        player.awardStat(Stats.ITEM_BROKEN.get(item))
        stack.damageValue = 0
    }

    /** Fracción (0 a 1) de los [limit] usos que le quedan; 1 si no hay límite. */
    fun remainingUses(used: Int, limit: Int): Float =
        if (limit <= 0) 1f else ((limit - used).toFloat() / limit).coerceIn(0f, 1f)

    /** Para la barra: lo que le queda, por durabilidad o por usos ([usesLeft]), lo que sea menos. */
    fun remainingFraction(stack: ItemStack, usesLeft: Float): Float {
        val durability = (stack.maxDamage - stack.damageValue).toFloat() / stack.maxDamage
        return min(durability, usesLeft).coerceIn(0f, 1f)
    }

    fun barWidth(remaining: Float): Int = Math.round(remaining * 13f)

    fun barColor(remaining: Float): Int = Mth.hsvToRgb(remaining / 3f, 1f, 1f)
}
