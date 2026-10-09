package daio7771.dioriteisntuseless.abuse.world

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

/**
 * Families of blocks that can be swapped for one another: each block tag
 * dioriteisntuseless:swap_family/<name> is a family (data/dioriteisntuseless/tags/blocks/
 * swap_family/). Adding one only takes a new JSON file, from a datapack too.
 *
 * Besides being in a family, both the original and the replacement have to be simple, full
 * building blocks (see [isSimpleFullBlock]): even if someone puts a chest or a redstone block in
 * a tag, it is never swapped.
 */
object BlockFamilies {

    private const val PREFIX = "swap_family/"

    /**
     * For each block of some family, the other blocks of its family. If a block is in several,
     * it can become any of them (tuff: stone or andesite, from the stone family, and deepslate,
     * from the deepslate family). Computed every time (signals are rare) to follow the tags
     * after a /reload.
     */
    fun alternatives(): Map<Block, List<Block>> {
        val result = HashMap<Block, LinkedHashSet<Block>>()
        BuiltInRegistries.BLOCK.tags.forEach { pair ->
            val key = pair.first
            if (key.location.namespace != Dioriteisntuseless.MOD_ID || !key.location.path.startsWith(PREFIX)) return@forEach
            val members = pair.second.map { it.value() }.distinct()
            for (block in members) {
                result.getOrPut(block) { LinkedHashSet() } += members.filter { it != block }
            }
        }
        return result.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }
    }

    /**
     * A simple, full building block: no properties (no facing, stairs, slabs, doors, trapdoors,
     * rails...), except the axis of blocks like deepslate, which does not change what the block
     * does and is saved with the original for "Start over". No block entity (chests, furnaces,
     * signs...), no fluid, no redstone signal, and the shape of a full cube. Diorite is never
     * touched.
     */
    fun isSimpleFullBlock(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean =
        state.properties.all { it == BlockStateProperties.AXIS } &&
            !state.hasBlockEntity() &&
            state.fluidState.isEmpty &&
            !state.isSignalSource &&
            !state.`is`(Blocks.DIORITE) &&
            state.isCollisionShapeFullBlock(level, pos) &&
            Block.isShapeFullBlock(state.getShape(level, pos))
}
