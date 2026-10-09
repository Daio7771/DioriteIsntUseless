package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.network.AbuseStatePacket
import daio7771.dioriteisntuseless.network.StartOverPacket
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer

/**
 * What the affected player's client needs to know about the Abuse Mode (AbuseStatePacket), and the
 * "Start over" button of the credits (StartOverPacket).
 */
object AbuseStateSync {

    fun init() {
        ServerPlayNetworking.registerGlobalReceiver(StartOverPacket.TYPE) { _, player, _ -> onStartOverPressed(player) }
    }

    /**
     * Sends the state to the player's client (when it changes, on level up, on join, after
     * "Start over" and when the Abuse Mode is enabled or disabled).
     */
    fun sync(player: ServerPlayer) {
        if (!ServerPlayNetworking.canSend(player, AbuseStatePacket.TYPE)) return
        val state = AbuseData.get(player.server).getIfPresent(player.uuid)
        val creditsDue = state != null && Ending.creditsDue(state)
        // When disabled nothing plays (even if what was already done, like useless diorite, stays).
        val level = if (state != null && AbuseMode.active) state.level else 0
        ServerPlayNetworking.send(player, AbuseStatePacket(DioriteUselessness.isUseless(player), creditsDue, level))
    }

    /** Only accepted if the credits really are due: a client cannot start over whenever it wants. */
    private fun onStartOverPressed(player: ServerPlayer) {
        if (!AbuseMode.healthy) return
        AbuseMode.guard("start over from the credits") {
            val state = AbuseData.get(player.server).getIfPresent(player.uuid)
            if (state == null || !Ending.creditsDue(state)) {
                LOGGER.debug("Abuse mode: ignoring a start over request from {}: no credits due.", player.gameProfile.name)
                sync(player)  // in case their client believed otherwise
                return
            }
            StartOver.run(player.server, player.uuid)
        }
    }
}
