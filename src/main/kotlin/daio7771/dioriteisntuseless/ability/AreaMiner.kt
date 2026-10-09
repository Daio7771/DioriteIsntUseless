package daio7771.dioriteisntuseless.ability

import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.DioriteUselessness
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.item.DioritinePickaxeItem
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import java.util.UUID

/**
 * Ability of the Dioritine Pickaxe: when breaking stone or an ore without Shift, it also breaks the
 * surrounding blocks that are stone or ore, in a 3x3 square facing the side that was hit
 * (pickaxe.sneakMode can invert the Shift behavior).
 *
 * Like TreeFeller, it hooks into PlayerBlockBreakEvents.AFTER: the block hit is already gone and
 * vanilla has not charged the pickaxe nor dropped its items yet. Everything happens on the
 * logical server.
 *
 * Each surrounding block uses 1 durability (vanilla charges the one hit). A strike that breaks at
 * least pickaxe.minBlocksForStrike blocks, counting the one hit, counts: for the pickaxe's strike
 * limit and for the Abuse Mode. The config is read once per hit.
 */
object AreaMiner {

    /** More than any player's reach: it is only used to find out which side was hit. */
    private const val REACH = 8.0

    private val UNIT_CUBE = listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))

    /**
     * Players with a strike in progress. The break events fired by the strike itself (so other
     * mods can react) must not trigger the ability again. Only used from the server thread.
     */
    private val mining = HashSet<UUID>()

    fun init() {
        PlayerBlockBreakEvents.AFTER.register(::onBlockBroken)
    }

    private fun onBlockBroken(level: Level, player: Player, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?) {
        if (level !is ServerLevel || player !is ServerPlayer || player.uuid in mining) return
        val pickaxe = player.mainHandItem
        if (!pickaxe.`is`(ModItems.DIORITINE_PICKAXE) || !DioritinePickaxeItem.isMineable(state)) return

        val config = ModConfig.current.pickaxe
        // Disabled, or in phase A of the Abuse Mode ending: a normal pickaxe, one block at a time.
        if (!config.enabled || DioriteUselessness.isUseless(player)) return
        if (!config.sneakMode.usesAbility(player.isShiftKeyDown)) return

        mining += player.uuid
        val extra = try {
            mineAround(level, player, pickaxe, pos, hitAxis(player, pos), config)
        } finally {
            mining -= player.uuid
        }
        // Edges and corners that break fewer blocks use durability, but are not a strike.
        if (extra + 1 >= config.minBlocksForStrike && !pickaxe.isEmpty) {
            DioritinePickaxeItem.addStrike(pickaxe, player)
            AbuseTracker.onStrike(player)
        }
    }

    /**
     * Axis of the side that was hit: the 3x3 goes in the perpendicular plane (wall: vertical;
     * floor or ceiling: horizontal). The block is already air, so it checks where the player's
     * line of sight crosses the cube it used to fill. If it does not cross it (the view just
     * changed), the axis they are looking along the most.
     */
    private fun hitAxis(player: ServerPlayer, pos: BlockPos): Direction.Axis {
        val eye = player.eyePosition
        val hit = AABB.clip(UNIT_CUBE, eye, eye.add(player.lookAngle.scale(REACH)), pos)
        return (hit?.direction ?: Direction.orderedByNearest(player)[0]).axis
    }

    /** Breaks whichever of the 8 surrounding blocks it can and returns how many it broke. */
    private fun mineAround(
        level: ServerLevel,
        player: ServerPlayer,
        pickaxe: ItemStack,
        origin: BlockPos,
        axis: Direction.Axis,
        config: DiuConfig.Pickaxe,
    ): Int {
        val dropsAtOrigin = ArrayList<ItemStack>()
        var mined = 0
        for (pos in around(origin, axis)) {
            // No durability for one more (the last point belongs to the block hit) or already broken: stop here.
            if (pickaxe.isEmpty || !DioritinePickaxeItem.canAffordExtraBlock(pickaxe, player)) break
            if (!level.isLoaded(pos) || !isMineableAt(level, pos, level.getBlockState(pos))) continue
            val drops = BlockBreaker.breakAsPlayer(level, player, pos, pickaxe) { isMineableAt(level, pos, it) } ?: continue
            mined++
            DioritinePickaxeItem.addBlockWear(pickaxe, player)
            if (config.dropsAtOrigin) {
                dropsAtOrigin += drops
            } else {
                for (drop in drops) Block.popResource(level, pos, drop)
            }
        }
        BlockBreaker.dropAllAt(level, origin, dropsAtOrigin)
        return mined
    }

    /**
     * Only stone and its ores (the pickaxe's tag), and in case a datapack puts something else in
     * the tag: nothing unbreakable nor with a block entity (chests, furnaces...).
     */
    private fun isMineableAt(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean =
        DioritinePickaxeItem.isMineable(state) && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0f

    /** The 8 blocks around [origin] in the plane perpendicular to [axis]. */
    private fun around(origin: BlockPos, axis: Direction.Axis): List<BlockPos> {
        val result = ArrayList<BlockPos>(8)
        for (a in -1..1) for (b in -1..1) {
            if (a == 0 && b == 0) continue
            result += when (axis) {
                Direction.Axis.X -> origin.offset(0, a, b)
                Direction.Axis.Y -> origin.offset(a, 0, b)
                Direction.Axis.Z -> origin.offset(a, b, 0)
            }
        }
        return result
    }
}
