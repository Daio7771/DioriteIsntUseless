package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import net.minecraft.server.level.ServerPlayer

/**
 * Decide cuándo y qué señal recibe cada jugador, y nunca repite la misma señal dos veces seguidas
 * si hay otra posible.
 *
 * En los niveles 1 a 4 las señales van por acciones de abuso (cada árbol talado y cada picada 3x3
 * es una): cuanto más se abusa, más reacciona. Quien pica, que lo hace más a menudo, las recibe
 * antes; es a propósito. Cuando toca, la señal no llega con la acción sino un rato después, para
 * que no parezca que la causa. En el final las herramientas ya no hacen nada especial, así que
 * ahí van por tiempo jugado.
 *
 * Solo se usa desde el hilo del servidor, a través de AbuseTracker (que ya va protegido).
 */
object AbuseSignals {

    private val SIGNALS: List<AbuseSignal> = listOf(MorseSignal, NonsenseNameSignal, BlockSwapSignal, WordSignSignal)

    /** Acciones de abuso entre señales en los niveles 1 a 4. */
    private val ACTION_INTERVALS: List<IntRange> = listOf(4..6, 3..5, 2..4, 2..4)

    /** Ticks jugados desde que toca por acciones hasta que ocurre la señal (10 a 60 s). */
    private val DELAY_AFTER_ACTION = 200L..1_200L

    /** Ticks jugados entre señales en el final (4 a 7 minutos). */
    private val FINAL_INTERVAL = 4_800L..8_000L

    /** Si toca, lanza una señal y programa la siguiente. */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level <= 0) return
        if (state.nextSignalAt < 0 && state.signalDueAtActions < 0) {
            schedule(player, state)
        } else if (state.nextSignalAt >= 0 && state.playTicks >= state.nextSignalAt) {
            runOne(player, state)
            schedule(player, state)
        }
    }

    /** Lo llama AbuseTracker al contar un árbol o una picada: si con ella toca señal, la pone en camino. */
    fun onAbuseAction(player: ServerPlayer, state: PlayerAbuse) {
        if (state.signalDueAtActions < 0 || state.actions < state.signalDueAtActions) return
        state.signalDueAtActions = -1
        state.nextSignalAt = state.playTicks + randomIn(player, DELAY_AFTER_ACTION)
    }

    /**
     * Programa la próxima señal desde ahora y descarta la que estuviera en camino. Se llama también
     * al cambiar de nivel, para que la primera señal de un nivel nuevo no llegue justo al subir.
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

    /** Ids de las señales, para el comando de pruebas. */
    val ids: List<String> get() = SIGNALS.map { it.id }

    /**
     * Para el comando de pruebas: lanza ya una señal ([id], o una al azar si es null), aunque sea
     * de un nivel más alto. Devuelve false si no ha ocurrido (nivel 0, no hay sitio...).
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
     * Prueba las señales posibles en orden aleatorio, dejando la anterior para el final, hasta
     * que una ocurra de verdad (una señal en el mundo puede no encontrar sitio).
     */
    private fun runOne(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val candidates = SIGNALS.filter { state.level in it.minLevel..it.maxLevel && it.canRun(player, state) }.toMutableList()
        for (i in candidates.lastIndex downTo 1) {
            val j = player.random.nextInt(i + 1)
            candidates[i] = candidates[j].also { candidates[j] = candidates[i] }
        }
        candidates.sortBy { it.id == state.lastSignal }  // estable: la anterior pasa al final
        for (signal in candidates) {
            if (signal.run(player, state)) {
                state.lastSignal = signal.id
                return true
            }
        }
        return false
    }
}
