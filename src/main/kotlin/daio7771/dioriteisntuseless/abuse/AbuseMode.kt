package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.config.ModConfig

/**
 * Interruptor general del Abuse Mode (ver docs/claudeplans/HORROR_DESIGN.md).
 *
 * Regla de oro 1: nada del Abuse Mode puede tumbar el juego. Todo su código entra por [guard];
 * si algo falla, se registra en el log y el sistema se apaga hasta reiniciar el servidor (o
 * cerrar el mundo en un solo jugador). El resto del mod sigue funcionando.
 */
object AbuseMode {

    /** Un error interno ha apagado el sistema en esta sesión. Solo se usa desde el hilo del servidor. */
    private var failed = false

    /** true si hay que contar y reaccionar: activado en la configuración y sin errores en esta sesión. */
    val active: Boolean get() = !failed && ModConfig.current.abuseMode.enabled

    /** Al arrancar cada servidor (también el integrado al abrir un mundo). */
    fun resetSession() {
        failed = false
    }

    /**
     * Ejecuta [block] y, si lanza algo, apaga el sistema. Es inline para que [block] pueda usar
     * return. Los errores de la propia JVM (sin memoria, etc.) no se tragan.
     */
    inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            fail(what, e)
        }
    }

    @PublishedApi
    internal fun fail(what: String, error: Throwable) {
        if (!failed) {
            LOGGER.error("Abuse mode: internal error in {}. It is disabled until the world is reopened; " +
                "the rest of the mod keeps working.", what, error)
        }
        failed = true
    }
}
