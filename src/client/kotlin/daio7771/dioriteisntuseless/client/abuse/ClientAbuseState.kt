package daio7771.dioriteisntuseless.client.abuse

import daio7771.dioriteisntuseless.abuse.DioriteUselessness
import daio7771.dioriteisntuseless.network.AbuseStatePacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/** What the server tells this client about the Abuse Mode (see AbuseStatePacket). */
object ClientAbuseState {

    /** The credits are due: CreditsGate opens them at the next calm moment. */
    @Volatile
    var creditsDue = false

    /** Abuse Mode level (0 to 5; 0 if disabled): AbuseAmbience plays the matching background. */
    @Volatile
    var level = 0

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(AbuseStatePacket.TYPE) { packet, _, _ ->
            DioriteUselessness.clientFlag = packet.dioriteUseless
            creditsDue = packet.creditsDue
            level = packet.level
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            DioriteUselessness.clientFlag = false
            creditsDue = false
            level = 0
        }
    }
}
