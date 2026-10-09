package daio7771.dioriteisntuseless.abuse.world

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos

/**
 * What players could be seeing right now. Changes to the world only happen where nobody is looking
 * (golden rule 2: never seen changing), other players included.
 *
 * The server does not know each client's field of view, so a generous cone is used: 80° on each
 * side of the gaze (more than enough for a high FOV on a widescreen). Anything very close to
 * someone also counts as seen (peripheral vision).
 */
object Visibility {

    private val COS_HALF_CONE = cos(Math.toRadians(80.0))
    private const val MAX_DISTANCE = 96.0
    private const val TOO_CLOSE = 3.0

    /** true if no player in [level] could be seeing [pos] right now. */
    fun hiddenFromEveryone(level: ServerLevel, pos: BlockPos): Boolean = level.players().none { mightSee(it, pos) }

    fun mightSee(player: ServerPlayer, pos: BlockPos): Boolean {
        val toBlock = Vec3.atCenterOf(pos).subtract(player.eyePosition)
        val distance = toBlock.length()
        if (distance > MAX_DISTANCE) return false
        if (distance < TOO_CLOSE) return true
        return player.lookAngle.dot(toBlock.scale(1 / distance)) > COS_HALF_CONE
    }

    /** true if nothing blocks [pos] from the eyes of [player] (they will see it when turning there). */
    fun inLineOfSight(level: ServerLevel, player: ServerPlayer, pos: BlockPos): Boolean {
        val hit = level.clip(
            ClipContext(player.eyePosition, Vec3.atCenterOf(pos), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)
        )
        return hit.type == HitResult.Type.MISS || hit.blockPos == pos
    }
}
