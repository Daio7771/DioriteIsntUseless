package daio7771.dioriteisntuseless.item

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DiggerItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * Dioritine pickaxe: great with stone and its ores (the tag
 * dioriteisntuseless:dioritine_pickaxe_mineable) and useless for everything else: there it is
 * half as fast as a bare hand and drops nothing that needs a pickaxe. The 3x3 is done by AreaMiner.
 *
 * It is a DiggerItem and not a PickaxeItem because PickaxeItem only takes whole-number damage and
 * the pickaxe, like the axe, deals 0.5. It mines at iron level (diamond and emerald included).
 */
class DioritinePickaxeItem(properties: Item.Properties) :
    DiggerItem(ATTACK_DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER, DioritineTier, MINEABLE, properties) {

    /** Tier speed on stone and ores; on any other block, half the speed of a bare hand. */
    override fun getDestroySpeed(stack: ItemStack, state: BlockState): Float =
        if (isMineable(state)) speed else NON_STONE_SPEED

    /**
     * Uses 1 durability, like vanilla, and breaks the pickaxe if it has made all its strikes.
     * Vanilla calls this after deciding the drops of the block hit (with a copy of the pickaxe),
     * so the last block still drops its items. The other blocks of a strike are charged by
     * AreaMiner. In creative vanilla does not call this.
     */
    override fun mineBlock(stack: ItemStack, level: Level, state: BlockState, pos: BlockPos, miner: LivingEntity): Boolean {
        val result = super.mineBlock(stack, level, state, pos, miner)
        if (level.isClientSide || miner !is Player || stack.isEmpty) return result
        // If the limit is lowered in the config, a pickaxe already past it breaks on the next block.
        if (strikes(stack) >= ModConfig.current.pickaxe.strikesBeforeBreaking) ToolWear.breakInMainHand(stack, miner)
        return result
    }

    // The bar shows whatever is closer to breaking the pickaxe: the durability or the strikes.

    override fun isBarVisible(stack: ItemStack): Boolean = stack.isDamaged || remainingStrikesFraction(stack) < 1f

    override fun getBarWidth(stack: ItemStack): Int = ToolWear.barWidth(remainingFraction(stack))

    override fun getBarColor(stack: ItemStack): Int = ToolWear.barColor(remainingFraction(stack))

    private fun remainingFraction(stack: ItemStack): Float = ToolWear.remainingFraction(stack, remainingStrikesFraction(stack))

    private fun remainingStrikesFraction(stack: ItemStack): Float = ToolWear.remainingUses(strikes(stack), strikesBeforeBreaking())

    companion object {
        // Added to the player's base values: 1.0 damage and 4.0 attack speed.
        private const val ATTACK_DAMAGE_MODIFIER = -0.5f  // total damage 0.5, like the axe
        private const val ATTACK_SPEED_MODIFIER = -3.0f   // total speed 1.0, like the axe

        private const val NON_STONE_SPEED = 0.5f

        /** The only things it mines well (and in 3x3): Overworld stone, cobblestone and their ores. */
        val MINEABLE: TagKey<Block> = TagKey.create(Registries.BLOCK, Dioriteisntuseless.id("dioritine_pickaxe_mineable"))

        fun isMineable(state: BlockState): Boolean = state.`is`(MINEABLE)

        /** 3x3 strikes made since it was made or repaired on an anvil with ingots, in the pickaxe's NBT. */
        private const val STRIKES_TAG = "DioritineStrikes"

        /**
         * pickaxe.strikesBeforeBreaking of the remote server this client is connected to, or
         * null (dedicated server, single player or not connected: the local config applies).
         * Only the bar uses it; breaking is always decided by the server with its own config.
         */
        @Volatile
        var serverStrikesBeforeBreaking: Int? = null

        private fun strikesBeforeBreaking(): Int =
            serverStrikesBeforeBreaking ?: ModConfig.current.pickaxe.strikesBeforeBreaking

        fun strikes(stack: ItemStack): Int = stack.tag?.getInt(STRIKES_TAG) ?: 0

        /** true if [stack] is a dioritine pickaxe with strikes on its count. */
        @JvmStatic
        fun hasStrikes(stack: ItemStack): Boolean = stack.`is`(ModItems.DIORITINE_PICKAXE) && strikes(stack) > 0

        /** For the anvil: repairing with ingots resets the count. Does nothing to other items. */
        @JvmStatic
        fun resetStrikes(stack: ItemStack) {
            if (stack.`is`(ModItems.DIORITINE_PICKAXE)) stack.removeTagKey(STRIKES_TAG)
        }

        /**
         * Counts one strike. It does not break the pickaxe here: [mineBlock] does that right
         * after, once the block hit already has its drops. Does not count in creative.
         */
        fun addStrike(stack: ItemStack, player: Player) {
            if (player.abilities.instabuild || stack.isEmpty) return
            stack.orCreateTag.putInt(STRIKES_TAG, strikes(stack) + 1)
        }

        /** 1 durability for every surrounding block a strike breaks. Nothing in creative. */
        fun addBlockWear(stack: ItemStack, player: Player) {
            if (player.abilities.instabuild || stack.isEmpty) return
            stack.hurtAndBreak(1, player) { it.broadcastBreakEvent(EquipmentSlot.MAINHAND) }
        }

        /**
         * true if it can still break a surrounding block without running out of durability: the
         * last point is kept for the block hit, which is charged afterwards and must drop its items.
         */
        fun canAffordExtraBlock(stack: ItemStack, player: Player): Boolean =
            player.abilities.instabuild || !stack.isDamageableItem || stack.maxDamage - stack.damageValue > 1
    }
}
