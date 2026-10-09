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
 * When the credits open: when they are due (ClientAbuseState) and the player is in a calm moment,
 * and never in the middle of a fight (if they were just hurt or are on fire, it waits).
 * They open on their own as soon as no screen is open (they do not interrupt the chat or the
 * inventory), and also in place of the pause menu or the bed screen. MinecraftMixin passes it
 * every screen that opens, so the credits take its place without the other one being seen.
 *
 * So nobody gets trapped: if they close the credits with Escape, they no longer open on their own
 * until the next session; the pause menu goes back to being the pause menu until the next session
 * too (if they were closed from it), and in bed they do not come back until the next time they
 * sleep.
 *
 * Everything is used from the client thread.
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

    /** Opens them on their own if they are due, no screen is open and it is a calm moment. */
    private fun openIfCalm(client: Minecraft) {
        if (!ClientAbuseState.creditsDue || autoDismissed || client.screen != null || client.overlay != null) return
        try {
            val player = client.player ?: return
            if (!player.isSleeping && isCalm(player)) client.setScreen(CreditsScreen(null))
        } catch (e: Exception) {
            // Golden rule 1: better no credits than no game. Not retried this session.
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
            // Golden rule 1: better no credits than no game.
            LOGGER.error("Could not open the credits.", e)
            screen
        }
    }

    private fun isCalm(player: LocalPlayer): Boolean =
        player.isAlive && player.hurtTime == 0 && !player.isOnFire && player.airSupply > 0

    /** Escape in the credits (only once the button is there). [next] is null if they opened on their own. */
    fun onDismissed(next: Screen?) {
        when (next) {
            null -> autoDismissed = true
            is InBedChatScreen -> bedDismissed = true
            else -> pauseDismissed = true
        }
    }

    /** The "Start over" button: asks the server, which checks that it really is due. */
    fun onStartOver() {
        ClientAbuseState.creditsDue = false
        if (ClientPlayNetworking.canSend(StartOverPacket.TYPE)) ClientPlayNetworking.send(StartOverPacket())
    }
}
