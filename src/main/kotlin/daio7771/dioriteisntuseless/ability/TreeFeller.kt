package daio7771.dioriteisntuseless.ability

import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.DioriteUselessness
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.item.DioritineAxeItem
import daio7771.dioriteisntuseless.registry.ModItems
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * Ability of the Dioritine Axe: when breaking a log without Shift, the whole tree comes down
 * (treeFelling.sneakMode can invert this).
 *
 * It hooks into PlayerBlockBreakEvents.AFTER, which vanilla+Fabric fire right after removing the
 * block hit and before using the tool and dropping its items. Everything happens on the logical
 * server.
 *
 * It also charges the wear of every log the axe breaks, felling or not, and counts whole trees
 * (the axe breaks at treeFelling.treesBeforeBreaking).
 * The config is read once per log hit, so a felling uses the same values from start to finish
 * even if it is reloaded at the same time.
 */
object TreeFeller {

    /**
     * Players with a felling in progress. The break events fired by the felling itself (so other
     * mods can react) must not trigger the ability again nor be charged twice. Only used from the
     * server thread.
     */
    private val felling = HashSet<UUID>()

    fun init() {
        PlayerBlockBreakEvents.AFTER.register(::onBlockBroken)
    }

    private fun onBlockBroken(level: Level, player: Player, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?) {
        if (level !is ServerLevel || player !is ServerPlayer || player.uuid in felling) return
        val axe = player.mainHandItem
        if (!axe.`is`(ModItems.DIORITINE_AXE) || !state.`is`(BlockTags.LOGS)) return

        val config = ModConfig.current.treeFelling
        // Phase A of the Abuse Mode ending: in that player's hands, no axe fells.
        if (!config.enabled || DioriteUselessness.isUseless(player)) {
            // Normal axe: 1 durability per log, like vanilla.
            DioritineAxeItem.addLogWear(axe, player, 1)
            return
        }

        // The log hit costs the same as the others, whether the tree is felled or not.
        DioritineAxeItem.addLogWear(axe, player, config.logsPerDurabilityPoint)
        if (!config.sneakMode.usesAbility(player.isShiftKeyDown) || axe.isEmpty) return

        felling += player.uuid
        val felled = try {
            fell(level, player, axe, pos, config)
        } finally {
            felling -= player.uuid
        }
        // A whole tree = a felling that breaks something besides the log hit. A single log does
        // not count. It is charged at the end, with the drops already on the ground. It is also
        // what the Abuse Mode counts.
        if (felled > 0) {
            DioritineAxeItem.addFelledTree(axe, player, config.treesBeforeBreaking)
            AbuseTracker.onTreeFelled(player)
        }
    }

    /** Returns how many logs it broke, not counting the one hit. */
    private fun fell(level: ServerLevel, player: ServerPlayer, axe: ItemStack, origin: BlockPos, config: DiuConfig.TreeFelling): Int {
        val dropsAtOrigin = ArrayList<ItemStack>()
        var felled = 0
        for (pos in findConnectedLogs(level, player, origin, config.maxLogs)) {
            if (axe.isEmpty) break  // the axe broke: the felling stops here
            val drops = BlockBreaker.breakAsPlayer(level, player, pos, axe) { it.`is`(BlockTags.LOGS) } ?: continue
            felled++
            DioritineAxeItem.addLogWear(axe, player, config.logsPerDurabilityPoint)
            if (config.dropsAtOrigin) {
                dropsAtOrigin += drops
            } else {
                for (drop in drops) Block.popResource(level, pos, drop)
            }
        }
        BlockBreaker.dropAllAt(level, origin, dropsAtOrigin)
        return felled
    }

    /**
     * BFS (without recursion) through the 26-block neighborhood from the log hit, which is
     * already air. Returns the other logs ordered by distance, at most [maxLogs] - 1. It does not
     * go through logs the player could not break and does not load chunks.
     */
    private fun findConnectedLogs(level: ServerLevel, player: ServerPlayer, origin: BlockPos, maxLogs: Int): List<BlockPos> {
        if (maxLogs <= 1) return emptyList()
        val found = ArrayList<BlockPos>()
        val visited = LongOpenHashSet()
        val queue = ArrayDeque<BlockPos>()
        val cursor = BlockPos.MutableBlockPos()
        visited.add(origin.asLong())
        queue.add(origin)

        search@ while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                if (dx == 0 && dy == 0 && dz == 0) continue
                cursor.setWithOffset(current, dx, dy, dz)
                if (!visited.add(cursor.asLong()) || !level.isLoaded(cursor)) continue
                val state = level.getBlockState(cursor)
                if (!state.`is`(BlockTags.LOGS) || !BlockBreaker.mayBreak(level, player, cursor, state)) continue
                val pos = cursor.immutable()
                found += pos
                if (found.size >= maxLogs - 1) break@search
                queue.add(pos)
            }
        }
        return found
    }
}
