package daio7771.dioriteisntuseless.worldgen

import com.mojang.serialization.Codec
import daio7771.dioriteisntuseless.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureType
import java.util.Optional

/**
 * The abandoned diorite mine: a one-floor maze of about 25x25 blocks underground, between Y 0
 * and 40, built from loose pieces (MineLayout), so it is different in every world. It is rare
 * (structure_set) and only where there is solid stone around it: never in the middle of a big
 * cave, an aquifer or the open air.
 */
class AbandonedMineStructure(settings: StructureSettings) : Structure(settings) {

    override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
        val chunk = context.chunkPos()
        val random = context.random()
        val x = chunk.minBlockX
        val z = chunk.minBlockZ
        val center = MineLayout.SIZE / 2
        val surface = context.chunkGenerator().getFirstOccupiedHeight(
            x + center, z + center, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState(),
        )
        // Its roof well under the ground, and its floor between MIN_Y and MAX_Y.
        val highest = minOf(MAX_Y, surface - MIN_COVER - MineLayout.HEIGHT)
        if (highest < MIN_Y) return Optional.empty()
        val y = MIN_Y + random.nextInt(highest - MIN_Y + 1)
        if (!solidAround(context, x, y, z)) return Optional.empty()
        val origin = BlockPos(x, y, z)
        return Optional.of(GenerationStub(origin) { builder -> MineLayout.addPieces(builder, origin, random) })
    }

    /** Stone (no air, no water or lava) at its corners and its middle, halfway up its corridors. */
    private fun solidAround(context: GenerationContext, x: Int, y: Int, z: Int): Boolean {
        val far = MineLayout.SIZE - 1
        val middle = MineLayout.SIZE / 2
        return listOf(0 to 0, far to 0, 0 to far, far to far, middle to middle).all { (dx, dz) ->
            val column = context.chunkGenerator().getBaseColumn(x + dx, z + dz, context.heightAccessor(), context.randomState())
            (1..2).all { dy ->
                val state = column.getBlock(y + dy)
                !state.isAir && state.fluidState.isEmpty
            }
        }
    }

    override fun type(): StructureType<*> = ModStructures.ABANDONED_MINE

    companion object {
        val CODEC: Codec<AbandonedMineStructure> = simpleCodec(::AbandonedMineStructure)

        private const val MIN_Y = 0
        private const val MAX_Y = 40

        /** At least this much ground above its roof. */
        private const val MIN_COVER = 8
    }
}
