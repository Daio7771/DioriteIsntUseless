package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import net.minecraft.server.level.ServerPlayer

/** One of the Abuse Mode signals (HORROR_DESIGN.md, section 4). Chosen by AbuseSignals. */
interface AbuseSignal {

    /** Stable identifier: saved so the same signal is not repeated twice in a row. */
    val id: String

    /** First level at which it can appear. */
    val minLevel: Int

    /** Last level at which it can appear (the ending is AbuseTracker.LEVEL_FINAL). */
    val maxLevel: Int get() = Int.MAX_VALUE

    /** false if it cannot happen right now for a reason that is cheap to check (for example, no phrases). */
    fun canRun(player: ServerPlayer, state: PlayerAbuse): Boolean = true

    /** Fires the signal. False if nothing happened in the end (for example, there was no room). */
    fun run(player: ServerPlayer, state: PlayerAbuse): Boolean
}
