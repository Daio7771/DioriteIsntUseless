package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import net.minecraft.server.level.ServerPlayer

/** Una de las señales del Abuse Mode (HORROR_DESIGN.md, apartado 4). La elige AbuseSignals. */
interface AbuseSignal {

    /** Identificador estable: se guarda para no repetir la misma señal dos veces seguidas. */
    val id: String

    /** Primer nivel en el que puede aparecer. */
    val minLevel: Int

    /** Último nivel en el que puede aparecer (el final es AbuseTracker.LEVEL_FINAL). */
    val maxLevel: Int get() = Int.MAX_VALUE

    /** false si ahora no puede ocurrir por algo barato de comprobar (por ejemplo, no hay frases). */
    fun canRun(player: ServerPlayer, state: PlayerAbuse): Boolean = true

    /** Lanza la señal. False si al final no ha ocurrido nada (por ejemplo, no había sitio). */
    fun run(player: ServerPlayer, state: PlayerAbuse): Boolean
}
