package daio7771.dioriteisntuseless.abuse.world

import daio7771.dioriteisntuseless.abuse.WorldChanges
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StandingSignBlock
import net.minecraft.world.level.block.WallSignBlock
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignText
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RotationSegment
import net.minecraft.world.phys.Vec3

/**
 * Abuse Mode signs: oak, waxed (they cannot be edited) and recorded in WorldChanges so "Start over"
 * removes them. They are only placed in an air block, and blocks in unloaded chunks are never read
 * (that would force them to load).
 */
object AbuseSigns {

    /** Standing sign at [pos], with its front facing [facing]. Null if it cannot stand there. */
    fun standing(level: ServerLevel, pos: BlockPos, facing: Vec3): BlockState? {
        if (!isFreeAir(level, pos) || !level.isLoaded(pos.below())) return null
        val center = Vec3.atBottomCenterOf(pos)
        // As if placed by someone standing at [facing] looking at the sign: their yaw satisfies
        // look = (-sin(yaw), cos(yaw)) in (x, z), and vanilla rotates the sign by yaw + 180.
        val yaw = (Mth.atan2(-(center.x - facing.x), center.z - facing.z) * Mth.RAD_TO_DEG).toFloat()
        val state = Blocks.OAK_SIGN.defaultBlockState()
            .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yaw + 180f))
        return state.takeIf { it.canSurvive(level, pos) }
    }

    /** Sign hanging on the wall behind [pos], with its front towards [front]. */
    fun onWall(level: ServerLevel, pos: BlockPos, front: Direction): BlockState? {
        if (!isFreeAir(level, pos) || !level.isLoaded(pos.relative(front.opposite))) return null
        val state = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, front)
        return state.takeIf { it.canSurvive(level, pos) }
    }

    /** Places the sign with [lines] (up to 4) and records it. False if it could not be done. */
    fun place(level: ServerLevel, pos: BlockPos, state: BlockState, lines: List<Component>, player: ServerPlayer): Boolean {
        if (!WorldChanges.change(level, pos, state, player.uuid)) return false
        val sign = level.getBlockEntity(pos) as? SignBlockEntity ?: return true  // already recorded
        var text = SignText()
        lines.take(4).forEachIndexed { i, line -> text = text.setMessage(i, line) }
        sign.setText(text, true)
        sign.setWaxed(true)
        return true
    }

    private fun isFreeAir(level: ServerLevel, pos: BlockPos): Boolean =
        level.isLoaded(pos) && level.getBlockState(pos).isAir && level.getFluidState(pos).isEmpty
}
