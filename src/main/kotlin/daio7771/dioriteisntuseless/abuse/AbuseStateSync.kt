package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.network.AbuseStatePacket
import daio7771.dioriteisntuseless.network.StartOverPacket
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer

/**
 * Lo que el cliente del jugador afectado necesita saber del Abuse Mode (AbuseStatePacket), y el
 * botón "Start over" de los créditos (StartOverPacket).
 */
object AbuseStateSync {

    fun init() {
        ServerPlayNetworking.registerGlobalReceiver(StartOverPacket.TYPE) { _, player, _ -> onStartOverPressed(player) }
    }

    /**
     * Manda el estado al cliente del jugador (al cambiar, al subir de nivel, al entrar, tras
     * "Start over" y al activar o desactivar el Abuse Mode).
     */
    fun sync(player: ServerPlayer) {
        if (!ServerPlayNetworking.canSend(player, AbuseStatePacket.TYPE)) return
        val state = AbuseData.get(player.server).getIfPresent(player.uuid)
        val creditsDue = state != null && Ending.creditsDue(state)
        // Desactivado, no suena nada (aunque lo ya hecho, como la diorita inútil, siga).
        val level = if (state != null && AbuseMode.active) state.level else 0
        ServerPlayNetworking.send(player, AbuseStatePacket(DioriteUselessness.isUseless(player), creditsDue, level))
    }

    /** Solo se acepta si de verdad le tocan los créditos: un cliente no puede reiniciarse cuando quiera. */
    private fun onStartOverPressed(player: ServerPlayer) {
        if (!AbuseMode.healthy) return
        AbuseMode.guard("start over from the credits") {
            val state = AbuseData.get(player.server).getIfPresent(player.uuid)
            if (state == null || !Ending.creditsDue(state)) {
                LOGGER.debug("Abuse mode: ignoring a start over request from {}: no credits due.", player.gameProfile.name)
                sync(player)  // por si su cliente creía otra cosa
                return
            }
            StartOver.run(player.server, player.uuid)
        }
    }
}
