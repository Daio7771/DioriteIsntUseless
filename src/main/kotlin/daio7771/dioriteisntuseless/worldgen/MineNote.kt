package daio7771.dioriteisntuseless.worldgen

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.network.chat.Component

/**
 * The notes left in an abandoned mine, on signs (MineLayout puts 2-4 in each). Always complete
 * sentences (the broken-off ones are The Miner's), in English in every language (KEEP_ENGLISH),
 * and never with the words "enough" or "stop".
 *
 * Texts in en_us.json: sign.dioriteisntuseless.mine.<id>.<line>, [lines] lines (a sign has 4).
 */
enum class MineNote(val id: String, val lines: Int, val zone: Zone) {
    BAD_IDEA("bad_idea", 1, Zone.ENTRANCE),
    GO_BACK("go_back", 1, Zone.ENTRANCE),
    WARM("warm", 3, Zone.MIDDLE),
    WHY_HERE("why_here", 3, Zone.CENTER),
    LAMPS("lamps", 3, Zone.CENTER),
    DONT_DIG("dont_dig", 3, Zone.CENTER);

    /** Where in the mine: the entrance and the dead ends, halfway, or near the crib room. */
    enum class Zone { ENTRANCE, MIDDLE, CENTER }

    /** Its lines for a sign, centered on its 4 rows (the empty ones are empty components). */
    fun signLines(): List<Component> {
        val top = (SIGN_LINES - lines) / 2
        return List(SIGN_LINES) { row ->
            val line = row - top + 1
            if (line in 1..lines) Component.translatable("sign.${Dioriteisntuseless.MOD_ID}.mine.$id.$line") else Component.empty()
        }
    }

    companion object {
        private const val SIGN_LINES = 4

        fun byId(id: String): MineNote? = entries.firstOrNull { it.id == id }
    }
}
