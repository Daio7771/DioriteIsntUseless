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
 * Record of everything the Abuse Mode changes in the world (golden rule 4), so "Start over" can
 * undo it. Saved with the world (data/dioriteisntuseless_world_changes.dat in the Overworld).
 *
 * Every change goes through [change], which places the block and records it at the same time:
 * there is no way to change the world without it being recorded. Changes and restorations are
 * done without notifying neighbors (see [SILENT]). When undoing, each block goes back to the
 * original only if it is still the one the mod placed; if the player changed it afterwards, their
 * change is respected. Blocks in unloaded chunks are restored when they load (also after a restart).
 *
 * Only used from the server thread.
 */
class WorldChanges private constructor() : SavedData() {

    private class Change(
        val owner: UUID,
        /** What was there before the mod's first change at this position. */
        val original: BlockState,
        /** What the mod placed last time. */
        var placed: BlockState,
        /** "Start over" requested: restore as soon as the chunk is loaded. */
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

        /** How often pending restorations are retried. */
        private const val RESTORE_INTERVAL = 20

        /**
         * Sent to clients, but without notifying neighboring blocks: an observer next to a block
         * that changes does not pulse and no redstone machine fires. There is no need to notify
         * them: the mod only swaps a solid block for another solid one, or air for a sign.
         */
        private const val SILENT = Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE

        fun get(server: MinecraftServer): WorldChanges =
            server.overworld().dataStorage.computeIfAbsent(::load, ::WorldChanges, NAME)

        /**
         * Places [state] at [pos] and records it as a change by [owner]. Returns false (touching
         * nothing) if the mod already changed that position for another player: that way each
         * "Start over" only undoes its own changes and never steps on someone else's.
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
                // Second change at the same spot: the original is still the one from before the first.
                existing.placed = state
                if (existing.restoring) {
                    existing.restoring = false
                    data.restoringCount--
                }
            }
            data.setDirty()
            return true
        }

        /** "Start over": undoes every change by [owner] (those in unloaded chunks, when they load). */
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

        /** From the server tick: applies the pending restorations whose chunk is already loaded. */
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
         * null if the saved block no longer exists (an uninstalled mod): readBlockState would turn
         * it into air, and restoring "air" could delete something of the player's.
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
                // The dimension no longer exists (an uninstalled mod): there is nothing to restore.
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
                if (!level.isLoaded(pos)) continue  // retried when the chunk loads
                // Only if it is still what the mod placed: if the player changed it, their change wins.
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
