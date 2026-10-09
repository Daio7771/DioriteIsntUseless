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
     * varias, puede cambiar a cualquiera de todas ellas (la toba: a piedra o andesita, de la
     * familia stone, y a pizarra profunda, de la familia deepslate). Se calcula cada vez (las
     * señales son raras) para seguir los tags tras un /reload.
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
     * Bloque de construcción simple y completo: sin propiedades (nada de orientación, escaleras,
     * losas, puertas, trampillas, raíles...), salvo el eje de bloques como la pizarra profunda,
     * que no cambia lo que hace el bloque y se guarda con el original para "Start over". Sin
     * entidad de bloque (cofres, hornos, carteles...), sin fluido, que no emite señal de redstone
     * y con forma de cubo entero. La diorita nunca se toca.
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
