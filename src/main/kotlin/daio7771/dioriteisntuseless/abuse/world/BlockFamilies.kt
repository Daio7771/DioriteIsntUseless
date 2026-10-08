package daio7771.dioriteisntuseless.abuse.world

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * Familias de bloques que se pueden cambiar entre sí: cada tag de bloques
 * dioriteisntuseless:swap_family/<nombre> es una familia (data/dioriteisntuseless/tags/blocks/
 * swap_family/). Para añadir una, basta con un JSON nuevo, también desde un datapack.
 *
 * Además de estar en una familia, origen y destino tienen que ser bloques de construcción
 * simples y completos (ver [isSimpleFullBlock]): aunque alguien meta en un tag un cofre o un
 * bloque de redstone, nunca se cambia.
 */
object BlockFamilies {

    private const val PREFIX = "swap_family/"

    /**
     * Para cada bloque de alguna familia, los demás bloques de su familia. Si un bloque está en
     * varias, vale la primera. Se calcula cada vez (las señales son raras) para seguir los tags
     * tras un /reload.
     */
    fun alternatives(): Map<Block, List<Block>> {
        val result = HashMap<Block, List<Block>>()
        BuiltInRegistries.BLOCK.tags.forEach { pair ->
            val key = pair.first
            if (key.location.namespace != Dioriteisntuseless.MOD_ID || !key.location.path.startsWith(PREFIX)) return@forEach
            val members = pair.second.map { it.value() }.distinct()
            for (block in members) {
                if (block !in result) result[block] = members.filter { it != block }
            }
        }
        return result.filterValues { it.isNotEmpty() }
    }

    /**
     * Bloque de construcción simple y completo: sin propiedades (nada de orientación, ejes,
     * escaleras, losas, puertas, trampillas, raíles...), sin entidad de bloque (cofres, hornos,
     * carteles...), sin fluido, que no emite señal de redstone y con forma de cubo entero.
     * La diorita nunca se toca.
     */
    fun isSimpleFullBlock(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean =
        state.properties.isEmpty() &&
            !state.hasBlockEntity() &&
            state.fluidState.isEmpty &&
            !state.isSignalSource &&
            !state.`is`(Blocks.DIORITE) &&
            state.isCollisionShapeFullBlock(level, pos) &&
            Block.isShapeFullBlock(state.getShape(level, pos))
}
