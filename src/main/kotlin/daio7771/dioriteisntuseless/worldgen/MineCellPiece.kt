package daio7771.dioriteisntuseless.worldgen

import daio7771.dioriteisntuseless.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext

/**
 * One corridor cell of the abandoned mine (MineLayout): [MineLayout.CELL] blocks square, a 3x3
 * corridor in the middle with stone all around, open towards its [openings]. Its shape comes from
 * them: one is a dead end, two in a line a straight stretch, two at a right angle a corner, three
 * a fork (four, a crossing).
 *
 * What may be in it: a wooden [beam] across a straight stretch, broken [rails] along it (with
 * gaps), a [crumb] at the closed end of a dead end, a small [spawner] in the middle. The [entrance]
 * is a straight stretch whose outer end has caved in.
 */
class MineCellPiece : StructurePiece {

    enum class Crumb { NONE, DIORITE, TORCH, PICKAXE }

    enum class Spawner(val type: () -> EntityType<*>?) {
        NONE({ null }), ZOMBIE({ EntityType.ZOMBIE }), SKELETON({ EntityType.SKELETON })
    }

    private val openings: Set<Direction>
    private val entrance: Boolean
    private val beam: Boolean
    private val rails: Boolean
    private val crumb: Crumb
    private val spawner: Spawner

    constructor(
        corner: BlockPos, openings: Set<Direction>, entrance: Boolean, beam: Boolean, rails: Boolean, crumb: Crumb, spawner: Spawner,
    ) : super(ModStructures.MINE_CELL, 0, box(corner)) {
        this.openings = openings
        this.entrance = entrance
        this.beam = beam
        this.rails = rails
        this.crumb = crumb
        this.spawner = spawner
    }

    constructor(tag: CompoundTag) : super(ModStructures.MINE_CELL, tag) {
        openings = Direction.Plane.HORIZONTAL.filter { tag.getInt(TAG_OPENINGS) and (1 shl it.get2DDataValue()) != 0 }.toSet()
        entrance = tag.getBoolean(TAG_ENTRANCE)
        beam = tag.getBoolean(TAG_BEAM)
        rails = tag.getBoolean(TAG_RAILS)
        crumb = Crumb.entries.getOrElse(tag.getInt(TAG_CRUMB)) { Crumb.NONE }
        spawner = Spawner.entries.getOrElse(tag.getInt(TAG_SPAWNER)) { Spawner.NONE }
    }

    override fun addAdditionalSaveData(context: StructurePieceSerializationContext, tag: CompoundTag) {
        tag.putInt(TAG_OPENINGS, openings.fold(0) { bits, it -> bits or (1 shl it.get2DDataValue()) })
        tag.putBoolean(TAG_ENTRANCE, entrance)
        tag.putBoolean(TAG_BEAM, beam)
        tag.putBoolean(TAG_RAILS, rails)
        tag.putInt(TAG_CRUMB, crumb.ordinal)
        tag.putInt(TAG_SPAWNER, spawner.ordinal)
    }

    override fun postProcess(
        level: WorldGenLevel, structureManager: StructureManager, generator: ChunkGenerator, random: RandomSource,
        box: BoundingBox, chunkPos: ChunkPos, pivot: BlockPos,
    ) = MineBlocks.guard("a corridor") { build(level, box) }

    private fun build(level: WorldGenLevel, box: BoundingBox) {
        val b = boundingBox
        val x0 = b.minX()
        val y0 = b.minY()
        val z0 = b.minZ()
        val last = MineLayout.CELL - 1
        MineBlocks.fillSolid(level, box, x0, y0, z0, b.maxX(), b.maxY(), b.maxZ())
        MineBlocks.fill(level, box, x0 + 1, y0 + 1, z0 + 1, x0 + last - 1, y0 + INSIDE, z0 + last - 1, AIR)
        for (side in openings) {
            when (side) {
                Direction.EAST -> MineBlocks.fill(level, box, x0 + last, y0 + 1, z0 + 1, x0 + last, y0 + INSIDE, z0 + last - 1, AIR)
                Direction.WEST -> MineBlocks.fill(level, box, x0, y0 + 1, z0 + 1, x0, y0 + INSIDE, z0 + last - 1, AIR)
                Direction.SOUTH -> MineBlocks.fill(level, box, x0 + 1, y0 + 1, z0 + last, x0 + last - 1, y0 + INSIDE, z0 + last, AIR)
                else -> MineBlocks.fill(level, box, x0 + 1, y0 + 1, z0, x0 + last - 1, y0 + INSIDE, z0, AIR)
            }
        }
        val axis = openings.firstOrNull()?.axis ?: Direction.Axis.X
        if (rails) brokenRails(level, box, axis)
        if (beam) beam(level, box, axis, MID)
        if (entrance) {
            // A second beam, nearer the cave-in: the entrance holds the roof up twice.
            beam(level, box, axis, if (openings.first().axisDirection == Direction.AxisDirection.POSITIVE) 1 else last - 1)
        }
        spawner.type()?.let { MineBlocks.spawner(level, box, BlockPos(x0 + MID, y0 + 1, z0 + MID), it) }
        if (crumb != Crumb.NONE) crumb(level, box)
    }

    /** Along the middle of the corridor, with gaps. */
    private fun brokenRails(level: WorldGenLevel, box: BoundingBox, axis: Direction.Axis) {
        val b = boundingBox
        val rail = Blocks.RAIL.defaultBlockState()
            .setValue(RailBlock.SHAPE, if (axis == Direction.Axis.X) RailShape.EAST_WEST else RailShape.NORTH_SOUTH)
        // Not into the stone of a closed end (the entrance's cave-in).
        val from = if (Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE) in openings) 0 else 1
        val to = if (Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE) in openings) MineLayout.CELL - 1 else MineLayout.CELL - 2
        for (along in from..to) {
            val x = if (axis == Direction.Axis.X) b.minX() + along else b.minX() + MID
            val z = if (axis == Direction.Axis.X) b.minZ() + MID else b.minZ() + along
            val y = b.minY() + 1
            if (MineBlocks.chance(x, y, z, RAIL_CHANCE)) MineBlocks.set(level, box, x, y, z, rail)
        }
    }

    /** A wooden frame across the corridor at [along] blocks from its west or north side: two posts and a lintel. */
    private fun beam(level: WorldGenLevel, box: BoundingBox, axis: Direction.Axis, along: Int) {
        val b = boundingBox
        val y = b.minY()
        for (across in 1..MineLayout.CELL - 2) {
            val x = if (axis == Direction.Axis.X) b.minX() + along else b.minX() + across
            val z = if (axis == Direction.Axis.X) b.minZ() + across else b.minZ() + along
            val post = across == 1 || across == MineLayout.CELL - 2
            if (post) {
                MineBlocks.set(level, box, x, y + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState())
                MineBlocks.set(level, box, x, y + 2, z, Blocks.SPRUCE_FENCE.defaultBlockState())
            }
            MineBlocks.set(level, box, x, y + INSIDE, z, Blocks.SPRUCE_PLANKS.defaultBlockState())
        }
    }

    /** At the closed end of a dead end: what someone left behind. */
    private fun crumb(level: WorldGenLevel, box: BoundingBox) {
        val open = openings.firstOrNull() ?: return
        val back = open.opposite
        val b = boundingBox
        val end = BlockPos(b.minX() + MID, b.minY() + 1, b.minZ() + MID).relative(back)
        val side = end.relative(back.clockWise)
        when (crumb) {
            Crumb.DIORITE -> {
                MineBlocks.set(level, box, end.x, end.y, end.z, Blocks.DIORITE.defaultBlockState())
                MineBlocks.set(level, box, side.x, side.y, side.z, Blocks.DIORITE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM))
            }
            Crumb.TORCH -> MineBlocks.leftBehind(level, box, end, ItemStack(Items.TORCH), back.toYRot())
            Crumb.PICKAXE -> MineBlocks.leftBehind(
                level, box, side, ItemStack(Items.STONE_PICKAXE).apply { damageValue = maxDamage - 3 }, back.toYRot(),
            )
            Crumb.NONE -> {}
        }
    }

    companion object {
        /** The corridor is 3 high: from 1 to [INSIDE] above the floor. */
        private const val INSIDE = 3
        private const val MID = MineLayout.CELL / 2
        private const val RAIL_CHANCE = 0.6f

        private val AIR = Blocks.CAVE_AIR.defaultBlockState()

        private const val TAG_OPENINGS = "Openings"
        private const val TAG_ENTRANCE = "Entrance"
        private const val TAG_BEAM = "Beam"
        private const val TAG_RAILS = "Rails"
        private const val TAG_CRUMB = "Crumb"
        private const val TAG_SPAWNER = "Spawner"

        private fun box(corner: BlockPos) = BoundingBox(
            corner.x, corner.y, corner.z,
            corner.x + MineLayout.CELL - 1, corner.y + MineLayout.CELL_HEIGHT - 1, corner.z + MineLayout.CELL - 1,
        )
    }
}
