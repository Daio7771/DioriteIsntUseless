package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.abuse.world.AbuseProtection
import daio7771.dioriteisntuseless.abuse.world.AbuseSigns
import daio7771.dioriteisntuseless.abuse.world.Visibility
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * THE sign (level 4, HORROR_DESIGN.md section 4.5): "Don't abuse / diorite. / Delete this mod /
 * immediately." (texts in en_us.json).
 *
 * It appears once on reaching level 4, near the player and at eye level (hanging on a wall or, if
 * there is none, standing on the ground), facing them and with nothing in between: they will see
 * it as soon as they turn or move. But never right in front of their eyes (nor in anyone's sight):
 * they have to find it. If there is no room right now, it retries every few seconds.
 */
object FinalSign {

    /** How often (in ticks played) it is attempted while it has not appeared. */
    private const val RETRY_INTERVAL = 100L
    private val HORIZONTAL_DISTANCE = 3..7

    private val LINES = (1..4).map { Component.translatable("sign.dioriteisntuseless.final.$it") }

    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level != AbuseTracker.MAX_LEVEL || state.finalSignPlacedAt >= 0) return
        if (state.playTicks % RETRY_INTERVAL != 0L) return
        if (tryPlace(player)) state.finalSignPlacedAt = state.playTicks
    }

    private fun tryPlace(player: ServerPlayer): Boolean {
        val level = player.serverLevel()
        val feet = player.blockPosition()
        val candidates = ArrayList<BlockPos>()
        val max = HORIZONTAL_DISTANCE.last
        for (dx in -max..max) for (dz in -max..max) {
            val distance = Math.sqrt((dx * dx + dz * dz).toDouble())
            if (distance >= HORIZONTAL_DISTANCE.first && distance <= max) candidates += feet.offset(dx, 0, dz)
        }
        // Fisher-Yates with the player's randomness.
        for (i in candidates.lastIndex downTo 1) {
            val j = player.random.nextInt(i + 1)
            candidates[i] = candidates[j].also { candidates[j] = candidates[i] }
        }
        // First hanging at eye level; if there is no spot at all, standing on the ground.
        for (column in candidates) {
            val pos = column.above()
            val sign = wallSignFacing(player, pos) ?: continue
            if (isGoodSpot(player, pos) && AbuseSigns.place(level, pos, sign, LINES, player)) return true
        }
        for (pos in candidates) {
            val sign = AbuseSigns.standing(level, pos, player.position()) ?: continue
            if (isGoodSpot(player, pos) && AbuseSigns.place(level, pos, sign, LINES, player)) return true
        }
        return false
    }

    /** A sign at [pos] hanging on a wall and facing the player, or null. */
    private fun wallSignFacing(player: ServerPlayer, pos: BlockPos): BlockState? {
        val toPlayer = player.position().subtract(Vec3.atCenterOf(pos))
        for (front in Direction.Plane.HORIZONTAL) {
            // The player has to be clearly in front of the sign, not to the side.
            if (toPlayer.dot(Vec3.atLowerCornerOf(front.normal)) < 1.5) continue
            AbuseSigns.onWall(player.serverLevel(), pos, front)?.let { return it }
        }
        return null
    }

    /** They will see it when turning (nothing in between) but nobody sees it now; and building there is allowed. */
    private fun isGoodSpot(player: ServerPlayer, pos: BlockPos): Boolean {
        val level = player.serverLevel()
        return Visibility.inLineOfSight(level, player, pos) &&
            Visibility.hiddenFromEveryone(level, pos) &&
            AbuseProtection.mayChange(level, pos, player)
    }
}
