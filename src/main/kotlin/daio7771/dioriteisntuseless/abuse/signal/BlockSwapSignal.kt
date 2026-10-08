package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.abuse.WorldChanges
import daio7771.dioriteisntuseless.abuse.world.AbuseProtection
import daio7771.dioriteisntuseless.abuse.world.BlockFamilies
import daio7771.dioriteisntuseless.abuse.world.Visibility
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import kotlin.math.sqrt

/**
 * Bloques que cambian (nivel 3 en adelante): el jugador vuelve a mirar una pared y ya no es igual.
 *
 * Unos pocos bloques de construcción (1–4; en el final, 4–12) a 8–24 bloques del jugador
 * cambian por otro de su familia (BlockFamilies). Solo bloques con alguna cara al aire (los que
 * se pueden ver), nunca a la vista de nadie, respetando las protecciones, y siempre registrados
 * para "Start over".
 */
object BlockSwapSignal : AbuseSignal {

    private const val MIN_DISTANCE = 8.0
    private const val MAX_DISTANCE = 24.0
    private val LEVEL_3_BLOCKS = 1..4
    private val FINAL_BLOCKS = 4..12

    /** Posiciones al azar que se miran como mucho por señal. */
    private const val ATTEMPTS = 600

    override val id = "block_swap"
    override val minLevel = 3

    override fun run(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val families = BlockFamilies.alternatives()
        if (families.isEmpty()) return false
        val level = player.serverLevel()
        val random = player.random
        val range = if (state.level >= AbuseTracker.LEVEL_FINAL) FINAL_BLOCKS else LEVEL_3_BLOCKS
        val wanted = range.first + random.nextInt(range.last - range.first + 1)
        val origin = player.blockPosition()
        val pos = BlockPos.MutableBlockPos()
        var changed = 0
        for (attempt in 0 until ATTEMPTS) {
            if (changed >= wanted) break
            val max = MAX_DISTANCE.toInt()
            // Un poco más hacia arriba que hacia abajo: paredes, no el subsuelo.
            pos.set(origin.x + random.nextInt(2 * max + 1) - max, origin.y + random.nextInt(19) - 6, origin.z + random.nextInt(2 * max + 1) - max)
            val distance = sqrt(pos.distSqr(origin))
            if (distance < MIN_DISTANCE || distance > MAX_DISTANCE || !level.isLoaded(pos)) continue
            val current = level.getBlockState(pos)
            val options = families[current.block] ?: continue
            if (!BlockFamilies.isSimpleFullBlock(level, pos, current) || !isExposed(level, pos)) continue
            if (!Visibility.hiddenFromEveryone(level, pos) || !AbuseProtection.mayChange(level, pos, player)) continue
            val target = options[random.nextInt(options.size)].defaultBlockState()
            if (!BlockFamilies.isSimpleFullBlock(level, pos, target)) continue
            if (WorldChanges.change(level, pos.immutable(), target, player.uuid)) changed++
        }
        return changed > 0
    }

    /**
     * Tiene alguna cara que se puede ver (al lado de aire, cristal, plantas...). Los vecinos en
     * chunks sin cargar no cuentan: leerlos obligaría a cargar el chunk.
     */
    private fun isExposed(level: ServerLevel, pos: BlockPos): Boolean = Direction.entries.any {
        val neighbor = pos.relative(it)
        level.isLoaded(neighbor) && !level.getBlockState(neighbor).canOcclude()
    }
}
