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
 * Blocks that change (level 3 and up): the player looks at a wall again and it is not the same.
 *
 * A few building blocks (1–4; at the ending, 4–12) 8–24 blocks away from the player are swapped
 * for another one of their family (BlockFamilies). Only blocks with some face exposed to air
 * (the ones that can be seen), never in anyone's sight, respecting protections, and always
 * recorded for "Start over".
 */
object BlockSwapSignal : AbuseSignal {

    private const val MIN_DISTANCE = 8.0
    private const val MAX_DISTANCE = 24.0
    private val LEVEL_3_BLOCKS = 1..4
    private val FINAL_BLOCKS = 4..12

    /** Most random positions looked at per signal. */
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
            // A bit more upwards than downwards: walls, not the underground.
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
     * It has some face that can be seen (next to air, glass, plants...). Neighbors in unloaded
     * chunks don't count: reading them would force the chunk to load.
     */
    private fun isExposed(level: ServerLevel, pos: BlockPos): Boolean = Direction.entries.any {
        val neighbor = pos.relative(it)
        level.isLoaded(neighbor) && !level.getBlockState(neighbor).canOcclude()
    }
}
