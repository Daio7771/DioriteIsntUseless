package daio7771.dioriteisntuseless.registry

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.worldgen.AbandonedMineStructure
import daio7771.dioriteisntuseless.worldgen.CribRoomPiece
import daio7771.dioriteisntuseless.worldgen.MineCellPiece
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType

/**
 * The abandoned diorite mine (AbandonedMineStructure) and its pieces. Where and how often it
 * generates is data: worldgen/structure/abandoned_mine.json, worldgen/structure_set and the biome
 * tag has_structure/abandoned_mine.
 */
object ModStructures {

    val ABANDONED_MINE: StructureType<AbandonedMineStructure> = Registry.register(
        BuiltInRegistries.STRUCTURE_TYPE, Dioriteisntuseless.id("abandoned_mine"),
        StructureType { AbandonedMineStructure.CODEC },
    )

    val MINE_CELL: StructurePieceType = Registry.register(
        BuiltInRegistries.STRUCTURE_PIECE, Dioriteisntuseless.id("abandoned_mine_cell"),
        StructurePieceType.ContextlessType { tag -> MineCellPiece(tag) },
    )

    val CRIB_ROOM: StructurePieceType = Registry.register(
        BuiltInRegistries.STRUCTURE_PIECE, Dioriteisntuseless.id("abandoned_mine_crib_room"),
        StructurePieceType.ContextlessType { tag -> CribRoomPiece(tag) },
    )

    /** Call from onInitialize: accessing the object registers everything above. */
    fun init() {}
}
