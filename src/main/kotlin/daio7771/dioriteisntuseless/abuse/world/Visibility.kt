package daio7771.dioriteisntuseless.abuse.world

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos

/**
 * Lo que los jugadores podrían estar viendo ahora mismo. Los cambios en el mundo solo ocurren
 * donde no mira nadie (regla de oro 2: nunca se le ve cambiar), tampoco otros jugadores.
 *
 * El servidor no sabe el campo de visión de cada cliente, así que se usa un cono generoso: 80°
 * a cada lado de la mirada (cubre de sobra un FOV alto en pantalla panorámica). Lo que esté muy
 * cerca de alguien también cuenta como visto (visión periférica).
 */
object Visibility {

    private val COS_HALF_CONE = cos(Math.toRadians(80.0))
    private const val MAX_DISTANCE = 96.0
    private const val TOO_CLOSE = 3.0

    /** true si ningún jugador de [level] podría estar viendo [pos] ahora. */
    fun hiddenFromEveryone(level: ServerLevel, pos: BlockPos): Boolean = level.players().none { mightSee(it, pos) }

    fun mightSee(player: ServerPlayer, pos: BlockPos): Boolean {
        val toBlock = Vec3.atCenterOf(pos).subtract(player.eyePosition)
        val distance = toBlock.length()
        if (distance > MAX_DISTANCE) return false
        if (distance < TOO_CLOSE) return true
        return player.lookAngle.dot(toBlock.scale(1 / distance)) > COS_HALF_CONE
    }

    /** true si nada tapa [pos] desde los ojos de [player] (lo verá al girarse hacia allí). */
    fun inLineOfSight(level: ServerLevel, player: ServerPlayer, pos: BlockPos): Boolean {
        val hit = level.clip(
            ClipContext(player.eyePosition, Vec3.atCenterOf(pos), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)
        )
        return hit.type == HitResult.Type.MISS || hit.blockPos == pos
    }
}
