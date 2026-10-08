package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import net.minecraft.server.level.ServerPlayer

/** Una de las señales del Abuse Mode (HORROR_DESIGN.md, apartado 4). La elige AbuseSignals. */
interface AbuseSignal {

    /** Identificador estable: se guarda para no repetir la misma señal dos veces seguidas. */
    val id: String

    /** Primer nivel en el que puede aparecer. */
    val minLevel: Int

    /** false si ahora no puede ocurrir (por ejemplo, no hay frases o no hay sitio). */
    fun canRun(player: ServerPlayer, state: PlayerAbuse): Boolean = true

    fun run(player: ServerPlayer, state: PlayerAbuse)
}
