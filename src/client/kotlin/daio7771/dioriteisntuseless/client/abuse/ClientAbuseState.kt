package daio7771.dioriteisntuseless.client.abuse

import daio7771.dioriteisntuseless.abuse.DioriteUselessness
import daio7771.dioriteisntuseless.network.AbuseStatePacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/** Lo que el servidor le cuenta a este cliente del Abuse Mode (ver AbuseStatePacket). */
object ClientAbuseState {

    /** Tocan los créditos: CreditsGate los abre en el próximo momento tranquilo. */
    @Volatile
    var creditsDue = false

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(AbuseStatePacket.TYPE) { packet, _, _ ->
            DioriteUselessness.clientFlag = packet.dioriteUseless
            creditsDue = packet.creditsDue
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            DioriteUselessness.clientFlag = false
            creditsDue = false
        }
    }
}
