package daio7771.dioriteisntuseless.ability

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.min

/**
 * Romper bloques "como si los rompiera el jugador" para las habilidades de las herramientas
 * (TreeFeller, AreaMiner): mismas comprobaciones y eventos que vanilla, para respetar protecciones
 * y que otros mods puedan cancelarlo. Solo en el servidor.
 */
internal object BlockBreaker {

    /** Igual que vanilla (Block.playerDestroy). */
    private const val BREAK_EXHAUSTION = 0.005f

    /** Las mismas comprobaciones que hace vanilla antes de dejar romper un bloque. */
    fun mayBreak(level: ServerLevel, player: ServerPlayer, pos: BlockPos, state: BlockState): Boolean =
        level.mayInteract(player, pos) &&  // protección del spawn y borde del mundo
            !player.blockActionRestricted(level, pos, player.gameMode.gameModeForPlayer) &&  // aventura, espectador
            player.mainHandItem.item.canAttackBlock(state, level, pos, player)

    /**
     * Rompe el bloque de [pos] como si lo hubiera roto el jugador con [tool] (mismos pasos que
     * ServerPlayerGameMode.destroyBlock), pero devuelve sus drops en vez de soltarlos. Devuelve
     * null si no se ha roto (protegido, cancelado o ya no es un bloque que [accepts]).
     */
    fun breakAsPlayer(
        level: ServerLevel,
        player: ServerPlayer,
        pos: BlockPos,
        tool: ItemStack,
        accepts: (BlockState) -> Boolean,
    ): List<ItemStack>? {
        // El estado puede haber cambiado desde la búsqueda (otros mods reaccionan a cada rotura).
        val state = level.getBlockState(pos)
        if (!accepts(state) || !mayBreak(level, player, pos, state)) return null

        val blockEntity = level.getBlockEntity(pos)
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
            PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, player, pos, state, blockEntity)
            return null
        }

        val block = state.block
        block.playerWillDestroy(level, pos, state, player)
        if (!level.removeBlock(pos, false)) return null
        // playerWillDestroy manda las partículas y el sonido a todos menos al jugador, porque
        // vanilla da por hecho que su cliente ya predijo la rotura. Aquí no la predijo.
        player.connection.send(ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state), false))
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity)
        block.destroy(level, pos, state)

        // Como vanilla: en creativo no hay estadísticas, hambre ni drops.
        if (player.isCreative) return emptyList()
        player.awardStat(Stats.ITEM_USED.get(tool.item))
        if (!player.hasCorrectToolForDrops(state)) return emptyList()
        player.awardStat(Stats.BLOCK_MINED.get(block))
        player.causeFoodExhaustion(BREAK_EXHAUSTION)
        // Tabla de botín real del bloque, con la herramienta como contexto.
        val drops = Block.getDrops(state, level, pos, blockEntity, player, tool)
        state.spawnAfterBreak(level, pos, tool, true)
        return drops
    }

    /** Junta los drops iguales en pilas completas y los suelta en el bloque que golpeó el jugador. */
    fun dropAllAt(level: ServerLevel, origin: BlockPos, drops: List<ItemStack>) {
        val merged = ArrayList<ItemStack>()
        for (drop in drops) {
            for (stack in merged) {
                if (drop.isEmpty) break
                if (ItemStack.isSameItemSameTags(stack, drop)) {
                    val moved = min(drop.count, stack.maxStackSize - stack.count)
                    stack.grow(moved)
                    drop.shrink(moved)
                }
            }
            if (!drop.isEmpty) merged += drop
        }
        for (stack in merged) {
            Block.popResource(level, origin, stack)
        }
    }
}
