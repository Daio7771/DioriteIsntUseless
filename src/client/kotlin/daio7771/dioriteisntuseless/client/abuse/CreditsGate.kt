package daio7771.dioriteisntuseless.client.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.network.StartOverPacket
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.InBedChatScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer

/**
 * Cuándo se abren los créditos: cuando tocan (ClientAbuseState) y el jugador está en un momento
 * tranquilo, y nunca en mitad de un combate (si acaba de recibir daño o está ardiendo, se espera).
 * Se abren solos en cuanto no hay ninguna pantalla abierta (no interrumpen el chat ni el
 * inventario), y también en lugar del menú de pausa o de la cama. MinecraftMixin le pasa cada
 * pantalla que se abre, así que los créditos ocupan su lugar sin que se vea la otra.
 *
 * Para no dejar a nadie atrapado: si cierra los créditos con Escape, ya no se abren solos hasta
 * la próxima sesión; la pausa vuelve a ser la pausa también hasta la próxima sesión (si los cerró
 * desde ella), y en la cama no vuelven hasta la próxima vez que duerma.
 *
 * Todo se usa desde el hilo del cliente.
 */
object CreditsGate {

    private var autoDismissed = false
    private var pauseDismissed = false
    private var bedDismissed = false

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (bedDismissed && client.player?.isSleeping != true) bedDismissed = false
            openIfCalm(client)
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            autoDismissed = false
            pauseDismissed = false
            bedDismissed = false
        }
    }

    /** Los abre solos si tocan, no hay ninguna pantalla abierta y es un momento tranquilo. */
    private fun openIfCalm(client: Minecraft) {
        if (!ClientAbuseState.creditsDue || autoDismissed || client.screen != null || client.overlay != null) return
        try {
            val player = client.player ?: return
            if (!player.isSleeping && isCalm(player)) client.setScreen(CreditsScreen(null))
        } catch (e: Exception) {
            // Regla de oro 1: sin créditos antes que sin juego. No se reintenta en esta sesión.
            LOGGER.error("Could not open the credits.", e)
            autoDismissed = true
        }
    }

    @JvmStatic
    fun intercept(screen: Screen?): Screen? {
        if (!ClientAbuseState.creditsDue || screen is CreditsScreen) return screen
        return try {
            val player = Minecraft.getInstance().player ?: return screen
            val trigger = when (screen) {
                is PauseScreen -> !pauseDismissed
                is InBedChatScreen -> !bedDismissed
                else -> false
            }
            if (trigger && isCalm(player)) CreditsScreen(screen) else screen
        } catch (e: Exception) {
            // Regla de oro 1: sin créditos antes que sin juego.
            LOGGER.error("Could not open the credits.", e)
            screen
        }
    }

    private fun isCalm(player: LocalPlayer): Boolean =
        player.isAlive && player.hurtTime == 0 && !player.isOnFire && player.airSupply > 0

    /** Escape en los créditos (solo cuando ya está el botón). [next] es null si se abrieron solos. */
    fun onDismissed(next: Screen?) {
        when (next) {
            null -> autoDismissed = true
            is InBedChatScreen -> bedDismissed = true
            else -> pauseDismissed = true
        }
    }

    /** El botón "Start over": se lo pide al servidor, que comprueba que de verdad toca. */
    fun onStartOver() {
        ClientAbuseState.creditsDue = false
        if (ClientPlayNetworking.canSend(StartOverPacket.TYPE)) ClientPlayNetworking.send(StartOverPacket())
    }
}
