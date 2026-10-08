package daio7771.dioriteisntuseless.client.abuse

import daio7771.dioriteisntuseless.abuse.DioriteUselessness
import daio7771.dioriteisntuseless.network.AbuseStatePacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/** Lo que el servidor le cuenta a este cliente del Abuse Mode (ver AbuseStatePacket). */
object ClientAbuseState {

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(AbuseStatePacket.TYPE) { packet, _, _ ->
            DioriteUselessness.clientFlag = packet.dioriteUseless
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> DioriteUselessness.clientFlag = false }
    }
}
