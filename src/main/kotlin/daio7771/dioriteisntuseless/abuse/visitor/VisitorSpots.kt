package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.abuse.world.Visibility
import daio7771.dioriteisntuseless.entity.VisitorEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a visitor can appear next to a player without being seen appearing (golden rule 2):
 * - [minDistance] to [maxDistance] blocks away, in a direction at most [spread] degrees from
 *   straight behind them, and out of their sight (Visibility.mightSee, which also accounts for
 *   looking up or down). With `inFront`, the opposite: in front of them, in plain sight (only the
 *   ending does that).
 * - Standing on solid ground (not magma or a campfire), with room for its whole body and no
 *   liquid in it, inside the world border, and not where another visitor stands.
 * - With a clear line to the player: in the same open space as them, never on the other side of a
 *   wall, inside a building they are not in.
 */
object VisitorSpots {

    private const val ATTEMPTS = 48

    /** Feet heights tried, relative to the player's: a little up and down for uneven ground. */
    private val HEIGHTS = (2 downTo -3)

    fun find(
        player: ServerPlayer, type: EntityType<*>, minDistance: Double, maxDistance: Double, spread: Double, inFront: Boolean = false,
    ): Vec3? {
        val random = player.random
        val towards = if (inFront) 0.0 else 180.0
        repeat(ATTEMPTS) {
            val yaw = Math.toRadians(player.yRot + towards + (random.nextDouble() * 2 - 1) * spread)
            val distance = minDistance + random.nextDouble() * (maxDistance - minDistance)
            val column = BlockPos.containing(player.x - sin(yaw) * distance, player.y, player.z + cos(yaw) * distance)
            for (dy in HEIGHTS) {
                val spot = Vec3.atBottomCenterOf(column.above(dy))
                if (spot.distanceTo(player.position()) in minDistance - 0.5..maxDistance + 0.5 && fits(player, type, spot, inFront)) {
                    return spot
                }
            }
        }
        return null
    }

    /** [spot] (found by [find], behind them) is still free and out of their sight. */
    fun stillHidden(player: ServerPlayer, type: EntityType<*>, spot: Vec3): Boolean = fits(player, type, spot, false)

    private fun fits(player: ServerPlayer, type: EntityType<*>, spot: Vec3, inFront: Boolean): Boolean {
        val level = player.serverLevel()
        val feet = BlockPos.containing(spot)
        if (!level.hasChunkAt(feet)) return false
        val floorPos = feet.below()
        val floor = level.getBlockState(floorPos)
        if (!floor.isFaceSturdy(level, floorPos, Direction.UP) || floor.`is`(Blocks.MAGMA_BLOCK) || floor.`is`(BlockTags.CAMPFIRES)) {
            return false
        }
        val box = type.getAABB(spot.x, spot.y, spot.z)
        if (!level.worldBorder.isWithinBounds(box) || !level.noCollision(box) || level.containsAnyLiquid(box)) return false
        // Not inside another visitor (The Miner may be walking around).
        if (level.getEntitiesOfClass(VisitorEntity::class.java, box).isNotEmpty()) return false
        val center = box.center
        return clearPath(player, center) && clearPath(player, Vec3(center.x, box.maxY - 0.1, center.z)) &&
            (inFront || !Visibility.mightSee(player, box))
    }

    private fun clearPath(player: ServerPlayer, point: Vec3): Boolean =
        player.level().clip(
            ClipContext(player.eyePosition, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player)
        ).type == HitResult.Type.MISS
}
