package daio7771.dioriteisntuseless.ability

import daio7771.dioriteisntuseless.item.DioritineAxeItem
import daio7771.dioriteisntuseless.registry.ModItems
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID
import kotlin.math.min

/**
 * Habilidad del Dioritine Axe: al romper un tronco sin Shift, cae el árbol entero.
 *
 * Se engancha a PlayerBlockBreakEvents.AFTER, que vanilla+Fabric disparan justo después de
 * quitar el bloque golpeado y antes de gastar la herramienta y soltar sus drops. Todo ocurre
 * en el servidor lógico.
 */
object TreeFeller {

    /** Troncos como máximo por talada, contando el que golpea el jugador. */
    const val MAX_LOGS = 128

    /** Igual que vanilla (Block.playerDestroy). */
    private const val BREAK_EXHAUSTION = 0.005f

    /**
     * Jugadores con una talada en curso. Los eventos de rotura que dispara la propia talada
     * (para que otros mods puedan reaccionar) no deben volver a activar la habilidad ni cobrarse
     * dos veces. Solo se usa desde el hilo del servidor.
     */
    private val felling = HashSet<UUID>()

    fun init() {
        PlayerBlockBreakEvents.AFTER.register(::onBlockBroken)
    }

    private fun onBlockBroken(level: Level, player: Player, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?) {
        if (level !is ServerLevel || player !is ServerPlayer || player.uuid in felling) return
        val axe = player.mainHandItem
        if (!axe.`is`(ModItems.DIORITINE_AXE) || !state.`is`(BlockTags.LOGS)) return

        // El tronco golpeado cuesta 0.5 igual que los demás, con o sin Shift.
        DioritineAxeItem.addLogWear(axe, player)
        if (player.isShiftKeyDown || axe.isEmpty) return

        felling += player.uuid
        try {
            fell(level, player, axe, pos)
        } finally {
            felling -= player.uuid
        }
    }

    private fun fell(level: ServerLevel, player: ServerPlayer, axe: ItemStack, origin: BlockPos) {
        val drops = ArrayList<ItemStack>()
        for (pos in findConnectedLogs(level, player, origin)) {
            if (axe.isEmpty) break  // el hacha se ha roto: la talada se detiene aquí
            if (breakLog(level, player, pos, axe, drops)) {
                DioritineAxeItem.addLogWear(axe, player)
            }
        }
        dropAllAt(level, origin, drops)
    }

    /**
     * BFS (sin recursión) por la vecindad de 26 bloques desde el tronco golpeado, que ya es aire.
     * Devuelve los demás troncos en orden de cercanía, como mucho MAX_LOGS - 1. No atraviesa
     * troncos que el jugador no podría romper ni carga chunks.
     */
    private fun findConnectedLogs(level: ServerLevel, player: ServerPlayer, origin: BlockPos): List<BlockPos> {
        val found = ArrayList<BlockPos>()
        val visited = LongOpenHashSet()
        val queue = ArrayDeque<BlockPos>()
        val cursor = BlockPos.MutableBlockPos()
        visited.add(origin.asLong())
        queue.add(origin)

        search@ while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                if (dx == 0 && dy == 0 && dz == 0) continue
                cursor.setWithOffset(current, dx, dy, dz)
                if (!visited.add(cursor.asLong()) || !level.isLoaded(cursor)) continue
                val state = level.getBlockState(cursor)
                if (!state.`is`(BlockTags.LOGS) || !mayBreak(level, player, cursor, state)) continue
                val pos = cursor.immutable()
                found += pos
                if (found.size >= MAX_LOGS - 1) break@search
                queue.add(pos)
            }
        }
        return found
    }

    /** Las mismas comprobaciones que hace vanilla antes de dejar romper un bloque. */
    private fun mayBreak(level: ServerLevel, player: ServerPlayer, pos: BlockPos, state: BlockState): Boolean =
        level.mayInteract(player, pos) &&  // protección del spawn y borde del mundo
            !player.blockActionRestricted(level, pos, player.gameMode.gameModeForPlayer) &&  // aventura, espectador
            player.mainHandItem.item.canAttackBlock(state, level, pos, player)

    /**
     * Rompe un tronco como si lo hubiera roto el jugador (mismos pasos que
     * ServerPlayerGameMode.destroyBlock), pero guarda sus drops en [drops] en vez de soltarlos
     * en su sitio. Devuelve false si no se ha roto (protegido, cancelado o ya no es un tronco).
     */
    private fun breakLog(
        level: ServerLevel,
        player: ServerPlayer,
        pos: BlockPos,
        axe: ItemStack,
        drops: MutableList<ItemStack>,
    ): Boolean {
        // El estado puede haber cambiado desde la búsqueda (otros mods reaccionan a cada rotura).
        val state = level.getBlockState(pos)
        if (!state.`is`(BlockTags.LOGS) || !mayBreak(level, player, pos, state)) return false

        val blockEntity = level.getBlockEntity(pos)
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
            PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, player, pos, state, blockEntity)
            return false
        }

        val block = state.block
        block.playerWillDestroy(level, pos, state, player)
        if (!level.removeBlock(pos, false)) return false
        // playerWillDestroy manda las partículas y el sonido a todos menos al jugador, porque
        // vanilla da por hecho que su cliente ya predijo la rotura. Aquí no la predijo.
        player.connection.send(ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state), false))
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity)
        block.destroy(level, pos, state)

        // Como vanilla: en creativo no hay estadísticas, hambre ni drops.
        if (!player.isCreative) {
            player.awardStat(Stats.ITEM_USED.get(axe.item))
            if (player.hasCorrectToolForDrops(state)) {
                player.awardStat(Stats.BLOCK_MINED.get(block))
                player.causeFoodExhaustion(BREAK_EXHAUSTION)
                // Tabla de botín real del bloque, con el hacha como herramienta.
                drops += Block.getDrops(state, level, pos, blockEntity, player, axe)
                state.spawnAfterBreak(level, pos, axe, true)
            }
        }
        return true
    }

    /** Junta los drops iguales en pilas completas y los suelta en el bloque que golpeó el jugador. */
    private fun dropAllAt(level: ServerLevel, origin: BlockPos, drops: List<ItemStack>) {
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
