package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import net.minecraft.server.level.ServerPlayer

/**
 * Decide cuándo y qué señal recibe cada jugador. Las señales son raras: entre una y otra pasa un
 * tiempo al azar (en ticks jugados por ese jugador) que depende del nivel, y nunca se repite la
 * misma señal dos veces seguidas si hay otra posible.
 *
 * Solo se usa desde el hilo del servidor, a través de AbuseTracker (que ya va protegido).
 */
object AbuseSignals {

    private val SIGNALS: List<AbuseSignal> = listOf(MorseSignal, NonsenseNameSignal)

    /**
     * Ticks jugados entre señales, por nivel (HORROR_DESIGN.md, apartado 4):
     * nivel 1, 0–1 al día; nivel 2, 1–2; niveles 3 y 4, 2–3.
     */
    private val INTERVALS: List<LongRange> = listOf(
        24_000L..60_000L,
        12_000L..24_000L,
        8_000L..12_000L,
        8_000L..12_000L,
    )

    /** Si toca, lanza una señal y programa la siguiente. */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level <= 0) return
        if (state.nextSignalAt < 0) {
            schedule(player, state)
        } else if (state.playTicks >= state.nextSignalAt) {
            runOne(player, state)
            schedule(player, state)
        }
    }

    /**
     * Programa la próxima señal desde ahora. Se llama también al cambiar de nivel, para que la
     * primera señal de un nivel nuevo no llegue justo al subir.
     */
    fun schedule(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level <= 0) {
            state.nextSignalAt = -1
            return
        }
        val range = INTERVALS[(state.level - 1).coerceAtMost(INTERVALS.lastIndex)]
        val wait = range.first + player.random.nextLong().mod(range.last - range.first + 1)
        state.nextSignalAt = state.playTicks + wait
    }

    /** Para el comando de pruebas: lanza una señal ya. Devuelve false si no hay ninguna posible. */
    fun forceNow(player: ServerPlayer, state: PlayerAbuse): Boolean {
        if (state.level <= 0) return false
        val ran = runOne(player, state)
        schedule(player, state)
        return ran
    }

    private fun runOne(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val candidates = SIGNALS.filter { state.level >= it.minLevel && it.canRun(player, state) }
        if (candidates.isEmpty()) return false
        val fresh = candidates.filter { it.id != state.lastSignal }.ifEmpty { candidates }
        val signal = fresh[player.random.nextInt(fresh.size)]
        signal.run(player, state)
        state.lastSignal = signal.id
        return true
    }
}
