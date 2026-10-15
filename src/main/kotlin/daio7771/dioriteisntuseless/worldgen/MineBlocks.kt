package daio7771.dioriteisntuseless.worldgen

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SpawnerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.BoundingBox

/**
 * What the abandoned mine is made of, and the blocks every piece places. Everything goes through
 * here so that a piece never writes outside the chunk being generated ([box]).
 *
 * The stone is a mix (stone, andesite, diorite, granite, tuff, cobblestone) picked from the
 * position, not from a random source: a piece is built one chunk at a time, and its walls must
 * look the same on both sides of a chunk border.
 */
object MineBlocks {

    private val WALL = weighted(
        Blocks.STONE to 35, Blocks.ANDESITE to 15, Blocks.DIORITE to 15, Blocks.GRANITE to 10, Blocks.TUFF to 10,
        Blocks.COBBLESTONE to 15,
    )
    private val FLOOR = weighted(
        Blocks.STONE to 30, Blocks.COBBLESTONE to 30, Blocks.ANDESITE to 20, Blocks.DIORITE to 10, Blocks.TUFF to 10,
    )

    private fun weighted(vararg blocks: Pair<net.minecraft.world.level.block.Block, Int>): List<BlockState> =
        blocks.flatMap { (block, weight) -> List(weight) { block.defaultBlockState() } }

    private fun pick(list: List<BlockState>, x: Int, y: Int, z: Int): BlockState =
        list[Math.floorMod(Mth.getSeed(x, y, z) ushr 16, list.size.toLong()).toInt()]

    fun wall(x: Int, y: Int, z: Int): BlockState = pick(WALL, x, y, z)
    fun floor(x: Int, y: Int, z: Int): BlockState = pick(FLOOR, x, y, z)

    /** Whether a position-based "random" number for ([x], [y], [z]) falls under [chance]. */
    fun chance(x: Int, y: Int, z: Int, chance: Float): Boolean =
        Math.floorMod(Mth.getSeed(x, y, z) ushr 20, 1000L) < (chance * 1000).toLong()

    fun set(level: WorldGenLevel, box: BoundingBox, x: Int, y: Int, z: Int, state: BlockState) {
        if (box.isInside(x, y, z)) level.setBlock(BlockPos(x, y, z), state, UPDATE)
    }

    /** Solid stone from ([x0], [y0], [z0]) to ([x1], [y1], [z1]): the floor at the bottom, walls and roof elsewhere. */
    fun fillSolid(level: WorldGenLevel, box: BoundingBox, x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int) {
        for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) {
            set(level, box, x, y, z, if (y == y0) floor(x, y, z) else wall(x, y, z))
        }
    }

    fun fill(level: WorldGenLevel, box: BoundingBox, x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, state: BlockState) {
        for (x in minOf(x0, x1)..maxOf(x0, x1)) for (y in y0..y1) for (z in minOf(z0, z1)..maxOf(z0, z1)) set(level, box, x, y, z, state)
    }

    /**
     * A small spawner of [type]: it only wakes up with a player very close, spawns few mobs and
     * only right next to itself.
     */
    fun spawner(level: WorldGenLevel, box: BoundingBox, pos: BlockPos, type: EntityType<*>) {
        if (!box.isInside(pos)) return
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), UPDATE)
        val spawner = level.getBlockEntity(pos) as? SpawnerBlockEntity ?: return
        val entity = CompoundTag().apply { putString("id", EntityType.getKey(type).toString()) }
        spawner.load(CompoundTag().apply {
            put("SpawnData", CompoundTag().apply { put("entity", entity) })
            putShort("Delay", 20)
            putShort("MinSpawnDelay", 300)
            putShort("MaxSpawnDelay", 900)
            putShort("SpawnCount", 2)
            putShort("MaxNearbyEntities", 3)
            putShort("RequiredPlayerRange", 8)
            putShort("SpawnRange", 2)
        })
    }

    /** [stack] lying on the floor at [pos] for good: something somebody left behind. */
    fun leftBehind(level: WorldGenLevel, box: BoundingBox, pos: BlockPos, stack: ItemStack, yaw: Float) {
        if (!box.isInside(pos)) return
        val item = ItemEntity(level.level, pos.x + 0.5, pos.y + 0.05, pos.z + 0.5, stack, 0.0, 0.0, 0.0)
        item.setUnlimitedLifetime()
        item.yRot = yaw
        level.addFreshEntity(item)
    }

    /** Never crash the world for a mine (golden rule 1): a piece that fails is left as it is. */
    inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Dioriteisntuseless.LOGGER.error("Abandoned mine: could not build {}.", what, e)
        }
    }

    /** Notify clients, no neighbour updates (world generation). */
    private const val UPDATE = 2
}
