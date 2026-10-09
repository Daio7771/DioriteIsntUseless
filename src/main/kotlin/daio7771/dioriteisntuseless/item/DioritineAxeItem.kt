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
 * Dioritine axe: great with wood and useless for everything else.
 * It extends AxeItem, so it keeps the vanilla axe behavior (stripping bark, scraping copper,
 * removing wax, disabling shields). The felling is done by TreeFeller.
 */
class DioritineAxeItem(properties: Item.Properties) :
    AxeItem(DioritineTier, ATTACK_DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER, properties) {

    /** Tier speed on logs (tag minecraft:logs); on any other block, half the speed of a bare hand. */
    override fun getDestroySpeed(stack: ItemStack, state: BlockState): Float =
        if (state.`is`(BlockTags.LOGS)) speed else NON_WOOD_SPEED

    /**
     * Logs do not use durability here: TreeFeller charges them as soon as they break (the one the
     * player hits too), before vanilla calls this method. That way the config is read in a single
     * place. Every other block uses 1, like any tool.
     */
    override fun mineBlock(stack: ItemStack, level: Level, state: BlockState, pos: BlockPos, miner: LivingEntity): Boolean {
        if (state.`is`(BlockTags.LOGS)) return true
        return super.mineBlock(stack, level, state, pos, miner)
    }

    // The bar shows whatever is closer to breaking the axe: the durability or the trees.

    override fun isBarVisible(stack: ItemStack): Boolean = stack.isDamaged || remainingTreesFraction(stack) < 1f

    override fun getBarWidth(stack: ItemStack): Int = ToolWear.barWidth(remainingFraction(stack))

    override fun getBarColor(stack: ItemStack): Int = ToolWear.barColor(remainingFraction(stack))

    private fun remainingFraction(stack: ItemStack): Float = ToolWear.remainingFraction(stack, remainingTreesFraction(stack))

    private fun remainingTreesFraction(stack: ItemStack): Float = ToolWear.remainingUses(felledTrees(stack), treesBeforeBreaking())

    companion object {
        // Added to the player's base values: 1.0 damage and 4.0 attack speed.
        private const val ATTACK_DAMAGE_MODIFIER = -0.5f  // total damage 0.5
        private const val ATTACK_SPEED_MODIFIER = -3.0f   // total speed 1.0

        private const val NON_WOOD_SPEED = 0.5f

        /**
         * Broken logs that have not used durability yet, in the axe's NBT.
         * (With logsPerDurabilityPoint = 2 this is the same as the old "half points".)
         */
        private const val WEAR_TAG = "DioritineWear"

        /**
         * Charges one broken log: every [logsPerDurabilityPoint] logs, 1 real point of damage.
         * It is exact and deterministic: the count is kept in the NBT. With 1 it charges 1 per
         * log, like a normal axe. Nothing is used in creative. If the axe breaks, the stack ends
         * up empty.
         */
        fun addLogWear(stack: ItemStack, player: Player, logsPerDurabilityPoint: Int) {
            if (player.abilities.instabuild || stack.isEmpty) return
            val wear = (stack.tag?.getInt(WEAR_TAG) ?: 0) + 1
            // If logsPerDurabilityPoint was lowered, the saved count may be over it: that charges too.
            if (wear < logsPerDurabilityPoint) {
                stack.orCreateTag.putInt(WEAR_TAG, wear)
                return
            }
            stack.removeTagKey(WEAR_TAG)
            stack.hurtAndBreak(1, player) { it.broadcastBreakEvent(EquipmentSlot.MAINHAND) }
        }

        /** Whole trees felled since it was made or repaired on an anvil with ingots, in the axe's NBT. */
        private const val TREES_TAG = "DioritineTrees"

        /**
         * treeFelling.treesBeforeBreaking of the remote server this client is connected to, or
         * null (dedicated server, single player or not connected: the local config applies).
         * Only the bar uses it; breaking is always decided by the server with its own config.
         */
        @Volatile
        var serverTreesBeforeBreaking: Int? = null

        private fun treesBeforeBreaking(): Int =
            serverTreesBeforeBreaking ?: ModConfig.current.treeFelling.treesBeforeBreaking

        fun felledTrees(stack: ItemStack): Int = stack.tag?.getInt(TREES_TAG) ?: 0

        /** true if [stack] is a dioritine axe with felled trees on its count. */
        @JvmStatic
        fun hasFelledTrees(stack: ItemStack): Boolean = stack.`is`(ModItems.DIORITINE_AXE) && felledTrees(stack) > 0

        /** For the anvil: repairing with ingots resets the count. Does nothing to other items. */
        @JvmStatic
        fun resetFelledTrees(stack: ItemStack) {
            if (stack.`is`(ModItems.DIORITINE_AXE)) stack.removeTagKey(TREES_TAG)
        }

        /**
         * Counts one whole tree felled. On reaching [treesBeforeBreaking] the axe breaks, whatever
         * durability it has left (even with Unbreaking). 0 = no limit.
         * Does not count in creative.
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
