package daio7771.dioriteisntuseless.worldgen

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LanternBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext

/**
 * The end of the abandoned mine: the crib room, 2x2 cells of MineLayout (an 8x8 room inside its
 * walls), with its only door on the [door] side, [doorAt] blocks along that wall.
 *
 * Something kept it: a floor of polished diorite, a row of small diorite cribs along one wall,
 * one lantern hanging from the ceiling (a steady light, it never flickers) and, against the back
 * wall, a bigger crib with the chest in it (loot table chests/abandoned_mine). No mobs come here
 * (the structure has no monster spawns, and no spawner is near).
 */
class CribRoomPiece : StructurePiece {

    private val door: Direction
    private val doorAt: Int

    constructor(corner: BlockPos, door: Direction, doorAt: Int) : super(ModStructures.CRIB_ROOM, 0, box(corner)) {
        this.door = door
        this.doorAt = doorAt
    }

    constructor(tag: CompoundTag) : super(ModStructures.CRIB_ROOM, tag) {
        door = Direction.from2DDataValue(tag.getInt(TAG_DOOR))
        doorAt = tag.getInt(TAG_DOOR_AT).coerceIn(2, SIZE - 3)
    }

    override fun addAdditionalSaveData(context: StructurePieceSerializationContext, tag: CompoundTag) {
        tag.putInt(TAG_DOOR, door.get2DDataValue())
        tag.putInt(TAG_DOOR_AT, doorAt)
    }

    override fun postProcess(
        level: WorldGenLevel, structureManager: StructureManager, generator: ChunkGenerator, random: RandomSource,
        box: BoundingBox, chunkPos: ChunkPos, pivot: BlockPos,
    ) = MineBlocks.guard("the crib room") { build(level, box, random) }

    private fun build(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
        val b = boundingBox
        val x0 = b.minX()
        val y0 = b.minY()
        val z0 = b.minZ()
        val last = SIZE - 1
        MineBlocks.fillSolid(level, box, x0, y0, z0, b.maxX(), b.maxY(), b.maxZ())
        MineBlocks.fill(level, box, x0 + 1, y0 + 1, z0 + 1, x0 + last - 1, y0 + INSIDE, z0 + last - 1, AIR)
        MineBlocks.fill(level, box, x0 + 1, y0, z0 + 1, x0 + last - 1, y0, z0 + last - 1, Blocks.POLISHED_DIORITE.defaultBlockState())
        // The door: 3 wide and 3 high, like the corridors.
        when (door) {
            Direction.EAST -> MineBlocks.fill(level, box, x0 + last, y0 + 1, z0 + doorAt - 1, x0 + last, y0 + 3, z0 + doorAt + 1, AIR)
            Direction.WEST -> MineBlocks.fill(level, box, x0, y0 + 1, z0 + doorAt - 1, x0, y0 + 3, z0 + doorAt + 1, AIR)
            Direction.SOUTH -> MineBlocks.fill(level, box, x0 + doorAt - 1, y0 + 1, z0 + last, x0 + doorAt + 1, y0 + 3, z0 + last, AIR)
            else -> MineBlocks.fill(level, box, x0 + doorAt - 1, y0 + 1, z0, x0 + doorAt + 1, y0 + 3, z0, AIR)
        }

        val room = Frame(b, door)
        val post = Blocks.DIORITE_WALL.defaultBlockState()
        val bed = Blocks.POLISHED_DIORITE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
        // The small cribs, against the side wall: a headboard and a little bed each.
        for (v in CRIB_ROWS) {
            room.set(level, box, 0, v, 1, post)
            room.set(level, box, 1, v, 1, bed)
        }
        // The big one at the back, with the chest in it.
        for (u in listOf(CHEST_U - 1, CHEST_U + 1)) {
            room.set(level, box, u, Frame.BACK_ROW, 1, post)
            room.set(level, box, u, Frame.BACK_ROW - 1, 1, post)
        }
        val chest = room.pos(CHEST_U, Frame.BACK_ROW, 1)
        createChest(level, box, random, chest, LOOT_TABLE, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, door))
        room.set(level, box, CHEST_U, LANTERN_V, INSIDE, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true))
    }

    /**
     * The room seen from its door: u goes along the wall to the right of the way in (0 against the
     * side wall with the cribs), v goes from the door (0) to the back wall ([BACK_ROW]).
     */
    private class Frame(box: BoundingBox, door: Direction) {
        private val back = door.opposite
        private val side = back.clockWise
        private val corner = BlockPos(
            if (side.stepX + back.stepX > 0) box.minX() + 1 else box.maxX() - 1,
            box.minY(),
            if (side.stepZ + back.stepZ > 0) box.minZ() + 1 else box.maxZ() - 1,
        )

        fun pos(u: Int, v: Int, y: Int): BlockPos = corner.relative(side, u).relative(back, v).above(y)

        fun set(level: WorldGenLevel, box: BoundingBox, u: Int, v: Int, y: Int, state: net.minecraft.world.level.block.state.BlockState) {
            val pos = pos(u, v, y)
            MineBlocks.set(level, box, pos.x, pos.y, pos.z, state)
        }

        companion object {
            const val BACK_ROW = SIZE - 3
        }
    }

    companion object {
        private const val SIZE = MineLayout.CELL * 2
        private const val INSIDE = MineLayout.HEIGHT - 2

        private val CRIB_ROWS = listOf(2, 4, 6)
        private const val CHEST_U = 4
        private const val LANTERN_V = 3

        val LOOT_TABLE = Dioriteisntuseless.id("chests/abandoned_mine")

        private val AIR = Blocks.CAVE_AIR.defaultBlockState()

        private const val TAG_DOOR = "Door"
        private const val TAG_DOOR_AT = "DoorAt"

        private fun box(corner: BlockPos) = BoundingBox(
            corner.x, corner.y, corner.z, corner.x + SIZE - 1, corner.y + MineLayout.HEIGHT - 1, corner.z + SIZE - 1,
        )
    }
}
