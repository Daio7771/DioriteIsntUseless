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
 * EL cartel (nivel 4, HORROR_DESIGN.md apartado 4.5): "Don't abuse / diorite. / Delete this mod /
 * immediately." (textos en en_us.json).
 *
 * Aparece una vez al llegar al nivel 4, cerca del jugador y a la altura de sus ojos (colgado de
 * una pared o, si no hay, de pie en el suelo), con el frente hacia él y sin nada en medio: lo
 * verá en cuanto se gire o se mueva. Pero nunca delante de sus ojos (ni a la vista de nadie):
 * tiene que encontrarlo. Si ahora no hay sitio, se reintenta cada pocos segundos.
 */
object FinalSign {

    /** Cada cuánto (en ticks jugados) se intenta mientras no haya aparecido. */
    private const val RETRY_INTERVAL = 100L
    private val HORIZONTAL_DISTANCE = 3..7

    private val LINES = (1..4).map { Component.translatable("sign.dioriteisntuseless.final.$it") }

    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level < AbuseTracker.MAX_LEVEL || state.finalSignPlacedAt >= 0) return
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
        // Fisher-Yates con el azar del jugador.
        for (i in candidates.lastIndex downTo 1) {
            val j = player.random.nextInt(i + 1)
            candidates[i] = candidates[j].also { candidates[j] = candidates[i] }
        }
        // Primero colgado a la altura de los ojos; si no hay ningún sitio, de pie en el suelo.
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

    /** Un cartel en [pos] colgado de una pared y con el frente hacia el jugador, o null. */
    private fun wallSignFacing(player: ServerPlayer, pos: BlockPos): BlockState? {
        val toPlayer = player.position().subtract(Vec3.atCenterOf(pos))
        for (front in Direction.Plane.HORIZONTAL) {
            // El jugador tiene que estar claramente delante del cartel, no de lado.
            if (toPlayer.dot(Vec3.atLowerCornerOf(front.normal)) < 1.5) continue
            AbuseSigns.onWall(player.serverLevel(), pos, front)?.let { return it }
        }
        return null
    }

    /** Lo verá al girarse (sin nada en medio) pero ahora no lo ve nadie; y se puede construir ahí. */
    private fun isGoodSpot(player: ServerPlayer, pos: BlockPos): Boolean {
        val level = player.serverLevel()
        return Visibility.inLineOfSight(level, player, pos) &&
            Visibility.hiddenFromEveryone(level, pos) &&
            AbuseProtection.mayChange(level, pos, player)
    }
}
