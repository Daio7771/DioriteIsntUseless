package daio7771.dioriteisntuseless.worldgen

import daio7771.dioriteisntuseless.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext

/**
 * The way down into an abandoned mine (MineLayout): a shaft one cell wide, from the mine's floor up
 * to the surface ([surface]: the first free block above the ground), opening into the entrance
 * corridor at the bottom ([opening]).
 *
 * Stone walls all the way up, so it stays whole even where it goes through a cave (it crosses it
 * like a pillar), a ladder against the wall facing the corridor, spruce posts at its inner corners,
 * and at the top a small mine mouth: four log posts with a slab roof, open on every side.
 */
class MineShaftPiece : StructurePiece {

    private val opening: Direction
    private val surface: Int

    constructor(corner: BlockPos, opening: Direction, surface: Int) : super(ModStructures.MINE_SHAFT, 0, box(corner, surface)) {
        this.opening = opening
        this.surface = surface
    }

    constructor(tag: CompoundTag) : super(ModStructures.MINE_SHAFT, tag) {
        opening = Direction.from2DDataValue(tag.getInt(TAG_OPENING))
        surface = tag.getInt(TAG_SURFACE)
    }

    /** Where they come out at the top: the middle of the mouth, at ground level. */
    val top: BlockPos get() = BlockPos(boundingBox.minX() + MID, surface, boundingBox.minZ() + MID)

    override fun addAdditionalSaveData(context: StructurePieceSerializationContext, tag: CompoundTag) {
        tag.putInt(TAG_OPENING, opening.get2DDataValue())
        tag.putInt(TAG_SURFACE, surface)
    }

    override fun postProcess(
        level: WorldGenLevel, structureManager: StructureManager, generator: ChunkGenerator, random: RandomSource,
        box: BoundingBox, chunkPos: ChunkPos, pivot: BlockPos,
    ) = MineBlocks.guard("the shaft") { build(level, box) }

    private fun build(level: WorldGenLevel, box: BoundingBox) {
        val b = boundingBox
        val x0 = b.minX()
        val y0 = b.minY()
        val z0 = b.minZ()
        val last = MineLayout.CELL - 1
        // Walls from the floor up to the ground, then the mouth above.
        MineBlocks.fillSolid(level, box, x0, y0, z0, x0 + last, surface - 1, z0 + last)
        MineBlocks.fill(level, box, x0 + 1, y0 + 1, z0 + 1, x0 + last - 1, b.maxY(), z0 + last - 1, AIR)
        MineBlocks.fill(level, box, x0, surface, z0, x0 + last, b.maxY(), z0 + last, Blocks.AIR.defaultBlockState())
        // Into the entrance corridor, 3 wide and 3 high.
        when (opening) {
            Direction.EAST -> MineBlocks.fill(level, box, x0 + last, y0 + 1, z0 + 1, x0 + last, y0 + 3, z0 + last - 1, AIR)
            Direction.WEST -> MineBlocks.fill(level, box, x0, y0 + 1, z0 + 1, x0, y0 + 3, z0 + last - 1, AIR)
            Direction.SOUTH -> MineBlocks.fill(level, box, x0 + 1, y0 + 1, z0 + last, x0 + last - 1, y0 + 3, z0 + last, AIR)
            else -> MineBlocks.fill(level, box, x0 + 1, y0 + 1, z0, x0 + last - 1, y0 + 3, z0, AIR)
        }

        val center = BlockPos(x0 + MID, y0, z0 + MID)
        // Posts at the inner corners, above the way into the corridor.
        for (dx in listOf(-1, 1)) for (dz in listOf(-1, 1)) {
            MineBlocks.fill(level, box, center.x + dx, y0 + 4, center.z + dz, center.x + dx, surface - 1, center.z + dz, POST)
        }
        // The ladder: against the wall facing the corridor, from the floor to the ground.
        val back = opening.opposite
        val ladder = center.relative(back)
        MineBlocks.fill(
            level, box, ladder.x, y0 + 1, ladder.z, ladder.x, surface, ladder.z,
            Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, opening),
        )
        // The ladder's last rung needs a wall above the ground: a log, like the mouth's posts.
        val behind = ladder.relative(back)
        MineBlocks.fill(level, box, behind.x, surface, behind.z, behind.x, surface + 1, behind.z, LOG)

        // The mouth: four posts and a roof.
        for (x in listOf(x0, x0 + last)) for (z in listOf(z0, z0 + last)) {
            MineBlocks.fill(level, box, x, surface, z, x, surface + MOUTH_HEIGHT - 1, z, LOG)
        }
        MineBlocks.fill(level, box, x0, surface + MOUTH_HEIGHT, z0, x0 + last, surface + MOUTH_HEIGHT, z0 + last, ROOF)
    }

    companion object {
        /** At least this long, so there is something to climb. */
        const val MIN_LENGTH = 4

        private const val MID = MineLayout.CELL / 2
        private const val MOUTH_HEIGHT = 3

        private val AIR = Blocks.CAVE_AIR.defaultBlockState()
        private val POST = Blocks.SPRUCE_FENCE.defaultBlockState()
        private val LOG = Blocks.SPRUCE_LOG.defaultBlockState()
        private val ROOF = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)

        private const val TAG_OPENING = "Opening"
        private const val TAG_SURFACE = "Surface"

        private fun box(corner: BlockPos, surface: Int) = BoundingBox(
            corner.x, corner.y, corner.z, corner.x + MineLayout.CELL - 1, surface + MOUTH_HEIGHT, corner.z + MineLayout.CELL - 1,
        )
    }
}
