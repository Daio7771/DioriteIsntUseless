package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import net.minecraft.server.level.ServerPlayer

/**
 * Decides when each player gets a signal and which one, and never repeats the same signal twice in
 * a row if another one is possible.
 *
 * At levels 1 to 4 signals go by abuse actions (every tree felled and every 3x3 strike is one):
 * the more the abuse, the more it reacts. Whoever mines, which happens more often, gets them
 * sooner; that is on purpose. When one is due, the signal does not come with the action but a
 * while later, so it does not seem to be caused by it. At the ending the tools no longer do
 * anything special, so there they go by time played.
 *
 * Only used from the server thread, through AbuseTracker (which is already guarded).
 */
object AbuseSignals {

    private val SIGNALS: List<AbuseSignal> = listOf(MorseSignal, NonsenseNameSignal, BlockSwapSignal, WordSignSignal)

    /** Abuse actions between signals at levels 1 to 4. */
    private val ACTION_INTERVALS: List<IntRange> = listOf(4..6, 3..5, 2..4, 2..4)

    /** Ticks played from the moment one is due by actions until the signal happens (10 to 60 s). */
    private val DELAY_AFTER_ACTION = 200L..1_200L

    /** Ticks played between signals at the ending (4 to 7 minutes). */
    private val FINAL_INTERVAL = 4_800L..8_000L

    /** If one is due, fires a signal and schedules the next one. */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level <= 0) return
        if (state.nextSignalAt < 0 && state.signalDueAtActions < 0) {
            schedule(player, state)
        } else if (state.nextSignalAt >= 0 && state.playTicks >= state.nextSignalAt) {
            runOne(player, state)
            schedule(player, state)
        }
    }

    /** Called by AbuseTracker when it counts a tree or a strike: if that makes a signal due, it puts it on its way. */
    fun onAbuseAction(player: ServerPlayer, state: PlayerAbuse) {
        if (state.signalDueAtActions < 0 || state.actions < state.signalDueAtActions) return
        state.signalDueAtActions = -1
        state.nextSignalAt = state.playTicks + randomIn(player, DELAY_AFTER_ACTION)
    }

    /**
     * Schedules the next signal from now on and drops any that was on its way. Also called on
     * level change, so the first signal of a new level does not arrive right at the level up.
     */
    fun schedule(player: ServerPlayer, state: PlayerAbuse) {
        state.nextSignalAt = -1
        state.signalDueAtActions = -1
        when {
            state.level <= 0 -> {}
            state.level >= AbuseTracker.LEVEL_FINAL -> state.nextSignalAt = state.playTicks + randomIn(player, FINAL_INTERVAL)
            else -> {
                val range = ACTION_INTERVALS[(state.level - 1).coerceAtMost(ACTION_INTERVALS.lastIndex)]
                state.signalDueAtActions = state.actions + range.first + player.random.nextInt(range.last - range.first + 1)
            }
        }
    }

    private fun randomIn(player: ServerPlayer, range: LongRange): Long =
        range.first + player.random.nextLong().mod(range.last - range.first + 1)

    /** Signal ids, for the testing command. */
    val ids: List<String> get() = SIGNALS.map { it.id }

    /**
     * For the testing command: fires a signal now ([id], or a random one if null), even if it
     * belongs to a higher level. Returns false if nothing happened (level 0, no room...).
     */
    fun forceNow(player: ServerPlayer, state: PlayerAbuse, id: String?): Boolean {
        if (state.level <= 0) return false
        val ran = if (id == null) {
            runOne(player, state)
        } else {
            val signal = SIGNALS.firstOrNull { it.id == id } ?: return false
            (signal.canRun(player, state) && signal.run(player, state)).also { if (it) state.lastSignal = signal.id }
        }
        schedule(player, state)
        return ran
    }

    /**
     * Tries the possible signals in random order, leaving the previous one for last, until one
     * actually happens (a signal in the world may not find a spot).
     */
    private fun runOne(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val candidates = SIGNALS.filter { state.level in it.minLevel..it.maxLevel && it.canRun(player, state) }.toMutableList()
        for (i in candidates.lastIndex downTo 1) {
            val j = player.random.nextInt(i + 1)
            candidates[i] = candidates[j].also { candidates[j] = candidates[i] }
        }
        candidates.sortBy { it.id == state.lastSignal }  // stable: the previous one moves to the end
        for (signal in candidates) {
            if (signal.run(player, state)) {
                state.lastSignal = signal.id
                return true
            }
        }
        return false
    }
}
