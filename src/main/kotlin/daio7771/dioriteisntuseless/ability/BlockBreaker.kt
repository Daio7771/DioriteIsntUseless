package daio7771.dioriteisntuseless.ability

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.min

/**
 * Breaking blocks "as if the player broke them" for the tool abilities (TreeFeller, AreaMiner):
 * the same checks and events as vanilla, so protections are respected and other mods can cancel
 * it. Server only.
 */
internal object BlockBreaker {

    /** Same as vanilla (Block.playerDestroy). */
    private const val BREAK_EXHAUSTION = 0.005f

    /** The same checks vanilla makes before letting a block be broken. */
    fun mayBreak(level: ServerLevel, player: ServerPlayer, pos: BlockPos, state: BlockState): Boolean =
        level.mayInteract(player, pos) &&  // spawn protection and world border
            !player.blockActionRestricted(level, pos, player.gameMode.gameModeForPlayer) &&  // adventure, spectator
            player.mainHandItem.item.canAttackBlock(state, level, pos, player)

    /**
     * Breaks the block at [pos] as if the player had broken it with [tool] (same steps as
     * ServerPlayerGameMode.destroyBlock), but returns its drops instead of dropping them. Returns
     * null if it was not broken (protected, cancelled or no longer a block that [accepts]).
     */
    fun breakAsPlayer(
        level: ServerLevel,
        player: ServerPlayer,
        pos: BlockPos,
        tool: ItemStack,
        accepts: (BlockState) -> Boolean,
    ): List<ItemStack>? {
        // The state may have changed since the search (other mods react to every break).
        val state = level.getBlockState(pos)
        if (!accepts(state) || !mayBreak(level, player, pos, state)) return null

        val blockEntity = level.getBlockEntity(pos)
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
            PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, player, pos, state, blockEntity)
            return null
        }

        val block = state.block
        block.playerWillDestroy(level, pos, state, player)
        if (!level.removeBlock(pos, false)) return null
        // playerWillDestroy sends the particles and the sound to everyone but the player, because
        // vanilla assumes their client already predicted the break. Here it did not.
        player.connection.send(ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state), false))
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity)
        block.destroy(level, pos, state)

        // Like vanilla: in creative there are no statistics, hunger or drops.
        if (player.isCreative) return emptyList()
        player.awardStat(Stats.ITEM_USED.get(tool.item))
        if (!player.hasCorrectToolForDrops(state)) return emptyList()
        player.awardStat(Stats.BLOCK_MINED.get(block))
        player.causeFoodExhaustion(BREAK_EXHAUSTION)
        // The block's real loot table, with the tool as context.
        val drops = Block.getDrops(state, level, pos, blockEntity, player, tool)
        state.spawnAfterBreak(level, pos, tool, true)
        return drops
    }

    /** Merges equal drops into full stacks and drops them at the block the player hit. */
    fun dropAllAt(level: ServerLevel, origin: BlockPos, drops: List<ItemStack>) {
        val merged = ArrayList<ItemStack>()
        for (drop in drops) {
            for (stack in merged) {
                if (drop.isEmpty) break
                if (ItemStack.isSameItemSameTags(stack, drop)) {
                    val moved = min(drop.count, stack.maxStackSize - stack.count)
                    stack.grow(moved)
                    drop.shrink(moved)
                }
            }
            if (!drop.isEmpty) merged += drop
        }
        for (stack in merged) {
            Block.popResource(level, origin, stack)
        }
    }
}
