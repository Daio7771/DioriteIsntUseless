package daio7771.dioriteisntuseless.worldgen

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.levelgen.structure.Structure

/**
 * Finds the abandoned mine nearest to a spot, the way /locate does, for The Miner's note about it
 * (MinerNotes). Only used from the server thread.
 */
object MineLocator {

    /** Where to go: the top of its shaft (or, without one, its entrance), and the height of its corridors. */
    class Found(val wayIn: BlockPos, val floorY: Int)

    private val TAG: TagKey<Structure> = TagKey.create(Registries.STRUCTURE, Dioriteisntuseless.id("abandoned_mine"))

    /** How far to look, in chunks: about 1600 blocks, more than the distance between two mines. */
    private const val RADIUS = 100

    fun nearest(level: ServerLevel, from: BlockPos): Found? {
        val found = level.findNearestMapStructure(TAG, from, RADIUS, false) ?: return null
        val structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Dioriteisntuseless.id("abandoned_mine"))
            ?: return null
        val chunk = level.getChunk(
            SectionPos.blockToSectionCoord(found.x), SectionPos.blockToSectionCoord(found.z), ChunkStatus.STRUCTURE_STARTS,
        )
        val start = level.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk)
        if (start == null || !start.isValid) return null
        val entrance = start.pieces.filterIsInstance<MineCellPiece>().firstOrNull { it.isEntrance } ?: return null
        val floorY = entrance.boundingBox.minY() + 1
        val shaft = start.pieces.filterIsInstance<MineShaftPiece>().firstOrNull()
        return Found(shaft?.top ?: entrance.boundingBox.center, floorY)
    }
}
