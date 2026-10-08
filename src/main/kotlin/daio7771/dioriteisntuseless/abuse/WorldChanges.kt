package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderGetter
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Registro de todo lo que el Abuse Mode cambia en el mundo (regla de oro 4), para deshacerlo con
 * "Start over". Se guarda con el mundo (data/dioriteisntuseless_world_changes.dat del Overworld).
 *
 * Todo cambio pasa por [change], que coloca el bloque y lo apunta a la vez: no hay forma de
 * cambiar el mundo sin dejarlo registrado. Cambios y restauraciones se hacen sin avisar a los
 * vecinos (ver [SILENT]). Al deshacer, cada bloque vuelve a ser el original solo
 * si todavía es el que puso el mod; si el jugador lo cambió después, se respeta su cambio. Los
 * bloques en chunks sin cargar se restauran cuando se cargan (también tras reiniciar).
 *
 * Solo se usa desde el hilo del servidor.
 */
class WorldChanges private constructor() : SavedData() {

    private class Change(
        val owner: UUID,
        /** Lo que había antes del primer cambio del mod en esta posición. */
        val original: BlockState,
        /** Lo que puso el mod la última vez. */
        var placed: BlockState,
        /** "Start over" pedido: restaurar en cuanto el chunk esté cargado. */
        var restoring: Boolean = false,
    )

    private val changes = HashMap<ResourceKey<Level>, HashMap<Long, Change>>()

    private var restoringCount = 0

    fun count(owner: UUID): Int = changes.values.sumOf { byPos -> byPos.values.count { it.owner == owner } }

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        for ((dimension, byPos) in changes) {
            for ((pos, change) in byPos) {
                list.add(CompoundTag().apply {
                    putString("Dimension", dimension.location().toString())
                    putLong("Pos", pos)
                    putUUID("Owner", change.owner)
                    put("Original", NbtUtils.writeBlockState(change.original))
                    put("Placed", NbtUtils.writeBlockState(change.placed))
                    putBoolean("Restoring", change.restoring)
                })
            }
        }
        tag.putInt("DataVersion", DATA_VERSION)
        tag.put("Changes", list)
        return tag
    }

    companion object {
        private val NAME = "${Dioriteisntuseless.MOD_ID}_world_changes"
        private const val DATA_VERSION = 1

        /** Cada cuánto se reintentan las restauraciones pendientes. */
        private const val RESTORE_INTERVAL = 20

        /**
         * Se manda a los clientes, pero sin avisar a los bloques vecinos: un observador pegado a
         * un bloque que cambia no da pulso y ninguna máquina de redstone se dispara. No hace falta
         * avisarlos: el mod solo cambia un bloque sólido por otro sólido, o aire por un cartel.
         */
        private const val SILENT = Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE

        fun get(server: MinecraftServer): WorldChanges =
            server.overworld().dataStorage.computeIfAbsent(::load, ::WorldChanges, NAME)

        /**
         * Pone [state] en [pos] y lo apunta como cambio de [owner]. Devuelve false (sin tocar
         * nada) si esa posición ya la cambió el mod para otro jugador: así cada "Start over"
         * deshace solo lo suyo y nunca pisa lo de otro.
         */
        fun change(level: ServerLevel, pos: BlockPos, state: BlockState, owner: UUID): Boolean {
            val data = get(level.server)
            val byPos = data.changes.getOrPut(level.dimension()) { HashMap() }
            val key = pos.asLong()
            val existing = byPos[key]
            if (existing != null && existing.owner != owner) return false
            val current = level.getBlockState(pos)
            if (!level.setBlock(pos, state, SILENT)) return false
            if (existing == null) {
                byPos[key] = Change(owner, current, state)
            } else {
                // Segundo cambio en el mismo sitio: el original sigue siendo el de antes del primero.
                existing.placed = state
                if (existing.restoring) {
                    existing.restoring = false
                    data.restoringCount--
                }
            }
            data.setDirty()
            return true
        }

        /** "Start over": deshace todos los cambios de [owner] (los de chunks sin cargar, al cargarse). */
        fun undoAll(server: MinecraftServer, owner: UUID) {
            val data = get(server)
            for (byPos in data.changes.values) {
                for (change in byPos.values) {
                    if (change.owner == owner && !change.restoring) {
                        change.restoring = true
                        data.restoringCount++
                    }
                }
            }
            data.setDirty()
            data.restorePending(server)
        }

        /** Desde el tick del servidor: aplica las restauraciones pendientes cuyo chunk ya esté cargado. */
        fun tick(server: MinecraftServer) {
            if (server.tickCount % RESTORE_INTERVAL != 0) return
            val data = get(server)
            if (data.restoringCount > 0) data.restorePending(server)
        }

        private fun load(tag: CompoundTag): WorldChanges {
            val data = WorldChanges()
            val blocks = BuiltInRegistries.BLOCK.asLookup()
            for (entry in tag.getList("Changes", Tag.TAG_COMPOUND.toInt())) {
                val compound = entry as CompoundTag
                val dimensionId = ResourceLocation.tryParse(compound.getString("Dimension"))
                if (dimensionId == null || !compound.hasUUID("Owner")) continue
                val original = readState(blocks, compound.getCompound("Original"))
                val placed = readState(blocks, compound.getCompound("Placed"))
                if (original == null || placed == null) {
                    LOGGER.warn("Abuse mode: forgetting a world change at {} in {}: one of its blocks no longer exists.",
                        BlockPos.of(compound.getLong("Pos")), dimensionId)
                    continue
                }
                val change = Change(compound.getUUID("Owner"), original, placed, compound.getBoolean("Restoring"))
                val dimension = ResourceKey.create(Registries.DIMENSION, dimensionId)
                data.changes.getOrPut(dimension) { HashMap() }[compound.getLong("Pos")] = change
                if (change.restoring) data.restoringCount++
            }
            return data
        }

        /**
         * null si el bloque guardado ya no existe (un mod desinstalado): readBlockState lo
         * convertiría en aire, y restaurar "aire" podría borrar algo del jugador.
         */
        private fun readState(blocks: HolderGetter<Block>, tag: CompoundTag): BlockState? {
            val state = NbtUtils.readBlockState(blocks, tag)
            val name = ResourceLocation.tryParse(tag.getString("Name")) ?: return null
            return state.takeIf { BuiltInRegistries.BLOCK.getKey(it.block) == name }
        }
    }

    private fun restorePending(server: MinecraftServer) {
        val dimensions = changes.entries.iterator()
        while (dimensions.hasNext()) {
            val (dimension, byPos) = dimensions.next()
            val level = server.getLevel(dimension)
            if (level == null) {
                // La dimensión ya no existe (un mod desinstalado): no hay nada que restaurar.
                val dropped = byPos.values.count { it.restoring }
                if (dropped > 0) {
                    LOGGER.warn("Abuse mode: dimension {} no longer exists; {} block(s) there cannot be restored.",
                        dimension.location(), dropped)
                    byPos.values.removeIf { it.restoring }
                    restoringCount -= dropped
                    setDirty()
                }
                continue
            }
            val iterator = byPos.entries.iterator()
            while (iterator.hasNext()) {
                val (key, change) = iterator.next()
                if (!change.restoring) continue
                val pos = BlockPos.of(key)
                if (!level.isLoaded(pos)) continue  // se intentará cuando se cargue el chunk
                // Solo si sigue siendo lo que puso el mod: si el jugador lo cambió, manda su cambio.
                if (level.getBlockState(pos).`is`(change.placed.block)) {
                    level.setBlock(pos, change.original, SILENT)
                }
                iterator.remove()
                restoringCount--
                setDirty()
            }
            if (byPos.isEmpty()) dimensions.remove()
        }
    }
}
