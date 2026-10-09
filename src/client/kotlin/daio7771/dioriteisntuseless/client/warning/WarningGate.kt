package daio7771.dioriteisntuseless.client.warning

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen

/**
 * Decides whether the warning screen goes before the main menu. MinecraftMixin passes it every
 * screen that opens: that way the main menu is not drawn for even one frame before the warning
 * (no flicker). It is shown even if the Abuse Mode is disabled.
 *
 * Only used from the client thread.
 */
object WarningGate {

    /** Already accepted this session: not shown again even if it could not be saved. */
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
            // Golden rule 1: better no warning than no game.
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
