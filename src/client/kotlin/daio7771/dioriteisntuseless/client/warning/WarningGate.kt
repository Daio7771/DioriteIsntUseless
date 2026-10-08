package daio7771.dioriteisntuseless.client.warning

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen

/**
 * Decide si la pantalla de aviso va delante del menú principal. MinecraftMixin le pasa cada
 * pantalla que se abre: así el menú principal ni siquiera llega a dibujarse un frame antes del
 * aviso (nada de parpadeos). Se muestra aunque el Abuse Mode esté desactivado.
 *
 * Solo se usa desde el hilo del cliente.
 */
object WarningGate {

    /** Ya aceptado en esta sesión: no se vuelve a mostrar aunque no se haya podido guardar. */
    private var dismissed = false

    @JvmStatic
    fun intercept(screen: Screen?): Screen? {
        if (dismissed || screen !is TitleScreen) return screen
        return try {
            if (ModConfig.current.client.warningShown) {
                dismissed = true
                screen
            } else {
                WarningScreen(screen)
            }
        } catch (e: Exception) {
            // Regla de oro 1: sin aviso antes que sin juego.
            LOGGER.error("Could not show the warning screen.", e)
            dismissed = true
            screen
        }
    }

    fun markShown() {
        dismissed = true
        val config = ModConfig.current
        ModConfig.save(config.copy(client = DiuConfig.Client(warningShown = true)))
    }
}
