package daio7771.dioriteisntuseless.abuse

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource

/**
 * Mensajes de la fase B del final, en el chat y sin remitente, cada vez más raros. Uno cada 15 s:
 *
 * 1. La frase tal cual.
 * 2. Las mismas palabras desordenadas, y alguna perdida (siempre igual, decidido por Daio).
 * 3 a 7. Desordenadas al azar, con cada vez más números, palabras perdidas y, al final, palabras
 *    cambiadas por USELESS.
 *
 * Y a los 2 minutos, "USELESS, USELESS, USELESS..." en rojo, una vez por segundo, 15 veces
 * (decidido por Daio). Un segundo después acaba la fase B: suena una última cueva y se abren los
 * créditos (Ending).
 *
 * Solo se usa desde el hilo del servidor, a través de Ending (que ya va protegido).
 */
object EndingMessages {

    /** Entre los mensajes que cambian. */
    const val INTERVAL_TICKS = 300L

    /** Mensajes que cambian, antes del último. */
    private const val PHRASES = 7

    /** Veces que se repite el último, una por segundo. */
    private const val LAST_REPEATS = 15
    private const val REPEAT_TICKS = 20L

    /** El primer USELESS llega donde tocaría el octavo mensaje: a los 2 minutos. */
    private const val LAST_STARTS_AT = (PHRASES + 1) * INTERVAL_TICKS

    const val COUNT = PHRASES + LAST_REPEATS

    /** Duración de la fase B: un segundo después del último USELESS (2 min 15 s). */
    const val DURATION_TICKS = LAST_STARTS_AT + LAST_REPEATS * REPEAT_TICKS

    /**
     * Un mensaje que ya tenía que haber llegado hace más de esto no se manda (datos guardados por
     * otra versión del mod, por ejemplo): nada de soltarlos todos de golpe. Lo normal es que
     * lleguen como mucho un segundo tarde.
     */
    private const val LATE_TICKS = 40L

    private const val SENTENCE = "The end is near, diorite is useless right now"
    private const val SCRAMBLED = "The end diorite now, useless is right"
    private const val LAST = "USELESS, USELESS, USELESS..."
    private const val USELESS = "USELESS"

    private val WORDS: List<String> = SENTENCE.lowercase().replace(",", "").split(' ')
    private val NUMBER_BOUNDS = intArrayOf(10, 100, 1_000, 10_000)

    /** Desde Ending.tick: manda los mensajes que ya tocan (como mucho llegan un segundo tarde). */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level != AbuseTracker.LEVEL_FINAL || state.endingStartedAt < 0) return
        val elapsed = state.playTicks - state.endingStartedAt
        while (state.endingMessagesSent < COUNT && dueAt(state.endingMessagesSent) <= elapsed) {
            val index = state.endingMessagesSent
            if (elapsed - dueAt(index) <= LATE_TICKS) send(player, index)
            state.endingMessagesSent++
        }
    }

    /** Cuántos mensajes tendría que haber recibido ya según el tiempo jugado en la fase B. */
    fun dueFor(state: PlayerAbuse): Int {
        if (state.endingStartedAt < 0) return 0
        val elapsed = state.playTicks - state.endingStartedAt
        return (0 until COUNT).count { dueAt(it) <= elapsed }
    }

    /** Ticks desde el comienzo de la fase B hasta el mensaje número [index] (0 a COUNT - 1). */
    private fun dueAt(index: Int): Long =
        if (index < PHRASES) (index + 1) * INTERVAL_TICKS else LAST_STARTS_AT + (index - PHRASES) * REPEAT_TICKS

    private fun send(player: ServerPlayer, index: Int) {
        val color = if (index >= PHRASES) ChatFormatting.DARK_RED else ChatFormatting.GRAY
        // Mensaje de sistema: sin "<nombre>" delante, y no queda en el log del servidor.
        player.sendSystemMessage(Component.literal(text(index, player.random)).withStyle(color))
    }

    /** El mensaje número [index] (0 a COUNT - 1). */
    private fun text(index: Int, random: RandomSource): String = when (index) {
        0 -> SENTENCE
        1 -> SCRAMBLED
        in PHRASES until COUNT -> LAST
        else -> broken(index - 1, random)
    }

    /**
     * La frase rota, de [level] 1 (mensaje 3) a 5 (mensaje 7): desordenada, sin hasta level / 2
     * palabras, con [level] números al azar y, en los dos últimos, 1 y 2 palabras cambiadas por
     * USELESS. La coma acaba en cualquier sitio.
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

    /** Un número de 1 a 4 cifras. */
    private fun number(random: RandomSource): String =
        random.nextInt(NUMBER_BOUNDS[random.nextInt(NUMBER_BOUNDS.size)]).toString()
}
