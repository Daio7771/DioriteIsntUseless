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
 * Carteles del Abuse Mode: de roble, encerados (no se pueden editar) y registrados en
 * WorldChanges para que "Start over" los quite. Solo se ponen en un bloque de aire, y nunca se
 * leen bloques de chunks sin cargar (eso obligaría a cargarlos).
 */
object AbuseSigns {

    /** Cartel de pie en [pos], con el frente mirando hacia [facing]. Null si ahí no se sostiene. */
    fun standing(level: ServerLevel, pos: BlockPos, facing: Vec3): BlockState? {
        if (!isFreeAir(level, pos) || !level.isLoaded(pos.below())) return null
        val center = Vec3.atBottomCenterOf(pos)
        // Como si lo pusiera alguien que está en [facing] mirando al cartel: su yaw cumple
        // mirada = (-sin(yaw), cos(yaw)) en (x, z), y vanilla gira el cartel yaw + 180.
        val yaw = (Mth.atan2(-(center.x - facing.x), center.z - facing.z) * Mth.RAD_TO_DEG).toFloat()
        val state = Blocks.OAK_SIGN.defaultBlockState()
            .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yaw + 180f))
        return state.takeIf { it.canSurvive(level, pos) }
    }

    /** Cartel colgado de la pared que hay detrás de [pos], con el frente hacia [front]. */
    fun onWall(level: ServerLevel, pos: BlockPos, front: Direction): BlockState? {
        if (!isFreeAir(level, pos) || !level.isLoaded(pos.relative(front.opposite))) return null
        val state = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, front)
        return state.takeIf { it.canSurvive(level, pos) }
    }

    /** Pone el cartel con [lines] (hasta 4) y lo registra. False si no se ha podido. */
    fun place(level: ServerLevel, pos: BlockPos, state: BlockState, lines: List<Component>, player: ServerPlayer): Boolean {
        if (!WorldChanges.change(level, pos, state, player.uuid)) return false
        val sign = level.getBlockEntity(pos) as? SignBlockEntity ?: return true  // ya está registrado
        var text = SignText()
        lines.take(4).forEachIndexed { i, line -> text = text.setMessage(i, line) }
        sign.setText(text, true)
        sign.setWaxed(true)
        return true
    }

    private fun isFreeAir(level: ServerLevel, pos: BlockPos): Boolean =
        level.isLoaded(pos) && level.getBlockState(pos).isAir && level.getFluidState(pos).isEmpty
}
