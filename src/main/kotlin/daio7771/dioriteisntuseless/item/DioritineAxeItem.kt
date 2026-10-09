package daio7771.dioriteisntuseless.item

import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Hacha de dioritina: muy buena con la madera e inútil para todo lo demás.
 * Hereda de AxeItem, así que conserva el comportamiento vanilla de hacha (quitar corteza,
 * raspar cobre, quitar cera, desactivar escudos). La tala la hace TreeFeller.
 */
class DioritineAxeItem(properties: Item.Properties) :
    AxeItem(DioritineTier, ATTACK_DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER, properties) {

    /** Velocidad del tier en troncos (tag minecraft:logs); en cualquier otro bloque, la mitad que a mano. */
    override fun getDestroySpeed(stack: ItemStack, state: BlockState): Float =
        if (state.`is`(BlockTags.LOGS)) speed else NON_WOOD_SPEED

    /**
     * Los troncos no gastan aquí: los cobra TreeFeller en cuanto se rompen (también el que golpea
     * el jugador), antes de que vanilla llame a este método. Así la configuración se lee en un
     * solo sitio. El resto de bloques gasta 1, como cualquier herramienta.
     */
    override fun mineBlock(stack: ItemStack, level: Level, state: BlockState, pos: BlockPos, miner: LivingEntity): Boolean {
        if (state.`is`(BlockTags.LOGS)) return true
        return super.mineBlock(stack, level, state, pos, miner)
    }

    // La barra muestra lo que esté más cerca de romper el hacha: la durabilidad o los árboles.

    override fun isBarVisible(stack: ItemStack): Boolean = stack.isDamaged || remainingTreesFraction(stack) < 1f

    override fun getBarWidth(stack: ItemStack): Int = ToolWear.barWidth(remainingFraction(stack))

    override fun getBarColor(stack: ItemStack): Int = ToolWear.barColor(remainingFraction(stack))

    private fun remainingFraction(stack: ItemStack): Float = ToolWear.remainingFraction(stack, remainingTreesFraction(stack))

    private fun remainingTreesFraction(stack: ItemStack): Float = ToolWear.remainingUses(felledTrees(stack), treesBeforeBreaking())

    companion object {
        // Se suman a los valores base del jugador: 1.0 de daño y 4.0 de velocidad de ataque.
        private const val ATTACK_DAMAGE_MODIFIER = -0.5f  // daño total 0.5
        private const val ATTACK_SPEED_MODIFIER = -3.0f   // velocidad total 1.0

        private const val NON_WOOD_SPEED = 0.5f

        /**
         * Troncos rotos que aún no han gastado durabilidad, en el NBT del hacha.
         * (Con logsPerDurabilityPoint = 2 equivale a los "medios puntos" de antes.)
         */
        private const val WEAR_TAG = "DioritineWear"

        /**
         * Cobra un tronco roto: cada [logsPerDurabilityPoint] troncos, 1 punto real de daño.
         * Es exacto y determinista: la cuenta se guarda en el NBT. Con 1 cobra 1 por tronco,
         * como un hacha normal. En creativo no se gasta nada. Si el hacha se rompe, la pila
         * queda vacía.
         */
        fun addLogWear(stack: ItemStack, player: Player, logsPerDurabilityPoint: Int) {
            if (player.abilities.instabuild || stack.isEmpty) return
            val wear = (stack.tag?.getInt(WEAR_TAG) ?: 0) + 1
            // Si se ha bajado logsPerDurabilityPoint, la cuenta guardada puede pasarse: también cobra.
            if (wear < logsPerDurabilityPoint) {
                stack.orCreateTag.putInt(WEAR_TAG, wear)
                return
            }
            stack.removeTagKey(WEAR_TAG)
            stack.hurtAndBreak(1, player) { it.broadcastBreakEvent(EquipmentSlot.MAINHAND) }
        }

        /** Árboles enteros talados desde que se hizo o se reparó en el yunque con lingotes, en el NBT del hacha. */
        private const val TREES_TAG = "DioritineTrees"

        /**
         * treeFelling.treesBeforeBreaking del servidor remoto al que está conectado este cliente, o
         * null (servidor dedicado, un solo jugador o sin conectar: vale la configuración local).
         * Solo lo usa la barra; la rotura la decide siempre el servidor con su configuración.
         */
        @Volatile
        var serverTreesBeforeBreaking: Int? = null

        private fun treesBeforeBreaking(): Int =
            serverTreesBeforeBreaking ?: ModConfig.current.treeFelling.treesBeforeBreaking

        fun felledTrees(stack: ItemStack): Int = stack.tag?.getInt(TREES_TAG) ?: 0

        /** true si [stack] es un hacha de dioritina con árboles talados en la cuenta. */
        @JvmStatic
        fun hasFelledTrees(stack: ItemStack): Boolean = stack.`is`(ModItems.DIORITINE_AXE) && felledTrees(stack) > 0

        /** Para el yunque: reparar con lingotes reinicia la cuenta. A otros ítems no les hace nada. */
        @JvmStatic
        fun resetFelledTrees(stack: ItemStack) {
            if (stack.`is`(ModItems.DIORITINE_AXE)) stack.removeTagKey(TREES_TAG)
        }

        /**
         * Cuenta un árbol entero talado. Al llegar a [treesBeforeBreaking] el hacha se rompe,
         * le quede la durabilidad que le quede (y aunque tenga Unbreaking). 0 = sin límite.
         * En creativo no cuenta.
         */
        fun addFelledTree(stack: ItemStack, player: Player, treesBeforeBreaking: Int) {
            if (player.abilities.instabuild || stack.isEmpty || treesBeforeBreaking <= 0) return
            val trees = felledTrees(stack) + 1
            if (trees < treesBeforeBreaking) {
                stack.orCreateTag.putInt(TREES_TAG, trees)
                return
            }
            ToolWear.breakInMainHand(stack, player)
        }
    }
}
