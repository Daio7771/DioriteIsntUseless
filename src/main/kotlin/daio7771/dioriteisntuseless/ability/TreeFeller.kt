package daio7771.dioriteisntuseless.ability

import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.DioriteUselessness
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.item.DioritineAxeItem
import daio7771.dioriteisntuseless.registry.ModItems
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * Habilidad del Dioritine Axe: al romper un tronco sin Shift, cae el árbol entero
 * (treeFelling.sneakMode puede invertirlo).
 *
 * Se engancha a PlayerBlockBreakEvents.AFTER, que vanilla+Fabric disparan justo después de
 * quitar el bloque golpeado y antes de gastar la herramienta y soltar sus drops. Todo ocurre
 * en el servidor lógico.
 *
 * También cobra el desgaste de todos los troncos que rompe el hacha, con o sin talada, y cuenta
 * los árboles enteros (el hacha se rompe a los treeFelling.treesBeforeBreaking).
 * La configuración se lee una vez por tronco golpeado, así una talada usa los mismos valores
 * de principio a fin aunque se recargue a la vez.
 */
object TreeFeller {

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

        val config = ModConfig.current.treeFelling
        // Fase A del final del Abuse Mode: en manos de ese jugador, ninguna hacha tala.
        if (!config.enabled || DioriteUselessness.isUseless(player)) {
            // Hacha normal: 1 de durabilidad por tronco, como vanilla.
            DioritineAxeItem.addLogWear(axe, player, 1)
            return
        }

        // El tronco golpeado cuesta lo mismo que los demás, se tale el árbol o no.
        DioritineAxeItem.addLogWear(axe, player, config.logsPerDurabilityPoint)
        if (!config.sneakMode.usesAbility(player.isShiftKeyDown) || axe.isEmpty) return

        felling += player.uuid
        val felled = try {
            fell(level, player, axe, pos, config)
        } finally {
            felling -= player.uuid
        }
        // Un árbol entero = una talada que rompe algo más que el tronco golpeado. Un tronco suelto
        // no cuenta. Se cobra al final, con los drops ya en el suelo. También es lo que cuenta el
        // Abuse Mode.
        if (felled > 0) {
            DioritineAxeItem.addFelledTree(axe, player, config.treesBeforeBreaking)
            AbuseTracker.onTreeFelled(player)
        }
    }

    /** Devuelve cuántos troncos ha roto, sin contar el golpeado. */
    private fun fell(level: ServerLevel, player: ServerPlayer, axe: ItemStack, origin: BlockPos, config: DiuConfig.TreeFelling): Int {
        val dropsAtOrigin = ArrayList<ItemStack>()
        var felled = 0
        for (pos in findConnectedLogs(level, player, origin, config.maxLogs)) {
            if (axe.isEmpty) break  // el hacha se ha roto: la talada se detiene aquí
            val drops = BlockBreaker.breakAsPlayer(level, player, pos, axe) { it.`is`(BlockTags.LOGS) } ?: continue
            felled++
            DioritineAxeItem.addLogWear(axe, player, config.logsPerDurabilityPoint)
            if (config.dropsAtOrigin) {
                dropsAtOrigin += drops
            } else {
                for (drop in drops) Block.popResource(level, pos, drop)
            }
        }
        BlockBreaker.dropAllAt(level, origin, dropsAtOrigin)
        return felled
    }

    /**
     * BFS (sin recursión) por la vecindad de 26 bloques desde el tronco golpeado, que ya es aire.
     * Devuelve los demás troncos en orden de cercanía, como mucho [maxLogs] - 1. No atraviesa
     * troncos que el jugador no podría romper ni carga chunks.
     */
    private fun findConnectedLogs(level: ServerLevel, player: ServerPlayer, origin: BlockPos, maxLogs: Int): List<BlockPos> {
        if (maxLogs <= 1) return emptyList()
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
                if (!state.`is`(BlockTags.LOGS) || !BlockBreaker.mayBreak(level, player, cursor, state)) continue
                val pos = cursor.immutable()
                found += pos
                if (found.size >= maxLogs - 1) break@search
                queue.add(pos)
            }
        }
        return found
    }
}
