package daio7771.dioriteisntuseless.abuse

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource

/**
 * Phase B messages of the ending, in chat and without a sender, stranger each time. One every 15 s:
 *
 * 1. The sentence as it is.
 * 2. The same words shuffled, with one missing (always the same, decided by Daio).
 * 3 to 7. Randomly shuffled, with more and more numbers, missing words and, at the end, words
 *    replaced by USELESS.
 *
 * And at 2 minutes, "USELESS, USELESS, USELESS..." in red, once per second, 15 times (decided by
 * Daio). One second later phase B ends: one last cave sound plays and the credits open (Ending).
 *
 * Only used from the server thread, through Ending (which is already guarded).
 */
object EndingMessages {

    /** Between the messages that change. */
    const val INTERVAL_TICKS = 300L

    /** Messages that change, before the last one. */
    private const val PHRASES = 7

    /** Times the last one is repeated, once per second. */
    private const val LAST_REPEATS = 15
    private const val REPEAT_TICKS = 20L

    /** The first USELESS comes where the eighth message would: at 2 minutes. */
    private const val LAST_STARTS_AT = (PHRASES + 1) * INTERVAL_TICKS

    const val COUNT = PHRASES + LAST_REPEATS

    /** Length of phase B: one second after the last USELESS (2 min 15 s). */
    const val DURATION_TICKS = LAST_STARTS_AT + LAST_REPEATS * REPEAT_TICKS

    /**
     * A message that was due more than this long ago is not sent (data saved by another version of
     * the mod, for example): never dump them all at once. Normally they arrive at most one second
     * late.
     */
    private const val LATE_TICKS = 40L

    private const val SENTENCE = "The end is near, diorite is useless right now"
    private const val SCRAMBLED = "The end diorite now, useless is right"
    private const val LAST = "USELESS, USELESS, USELESS..."
    private const val USELESS = "USELESS"

    private val WORDS: List<String> = SENTENCE.lowercase().replace(",", "").split(' ')
    private val NUMBER_BOUNDS = intArrayOf(10, 100, 1_000, 10_000)

    /** From Ending.tick: sends the messages that are due (they arrive at most one second late). */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level != AbuseTracker.LEVEL_FINAL || state.endingStartedAt < 0) return
        val elapsed = state.playTicks - state.endingStartedAt
        while (state.endingMessagesSent < COUNT && dueAt(state.endingMessagesSent) <= elapsed) {
            val index = state.endingMessagesSent
            if (elapsed - dueAt(index) <= LATE_TICKS) send(player, index)
            state.endingMessagesSent++
        }
    }

    /** How many messages they should have received by now, going by the time played in phase B. */
    fun dueFor(state: PlayerAbuse): Int {
        if (state.endingStartedAt < 0) return 0
        val elapsed = state.playTicks - state.endingStartedAt
        return (0 until COUNT).count { dueAt(it) <= elapsed }
    }

    /** Ticks from the start of phase B to message number [index] (0 to COUNT - 1). */
    private fun dueAt(index: Int): Long =
        if (index < PHRASES) (index + 1) * INTERVAL_TICKS else LAST_STARTS_AT + (index - PHRASES) * REPEAT_TICKS

    private fun send(player: ServerPlayer, index: Int) {
        val color = if (index >= PHRASES) ChatFormatting.DARK_RED else ChatFormatting.GRAY
        // System message: no "<name>" in front, and it does not end up in the server log.
        player.sendSystemMessage(Component.literal(text(index, player.random)).withStyle(color))
    }

    /** Message number [index] (0 to COUNT - 1). */
    private fun text(index: Int, random: RandomSource): String = when (index) {
        0 -> SENTENCE
        1 -> SCRAMBLED
        in PHRASES until COUNT -> LAST
        else -> broken(index - 1, random)
    }

    /**
     * The broken sentence, from [level] 1 (message 3) to 5 (message 7): shuffled, missing up to
     * level / 2 words, with [level] random numbers and, in the last two, 1 and 2 words replaced by
     * USELESS. The comma ends up anywhere.
     */
    private fun broken(level: Int, random: RandomSource): String {
        val words = WORDS.toMutableList()
        for (i in words.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            words[i] = words[j].also { words[j] = words[i] }
        }
        repeat(random.nextInt(level / 2 + 1)) { words.removeAt(random.nextInt(words.size)) }
        repeat((level - 3).coerceAtLeast(0)) { words[random.nextInt(words.size)] = USELESS }
        repeat(level) { words.add(random.nextInt(words.size + 1), number(random)) }
        val comma = 1 + random.nextInt(words.size - 2)
        words[comma] = words[comma] + ","
        return words.joinToString(" ").replaceFirstChar { it.uppercaseChar() }
    }

    /** A number with 1 to 4 digits. */
    private fun number(random: RandomSource): String =
        random.nextInt(NUMBER_BOUNDS[random.nextInt(NUMBER_BOUNDS.size)]).toString()
}
