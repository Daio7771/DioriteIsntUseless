package daio7771.dioriteisntuseless.client.abuse

import daio7771.dioriteisntuseless.network.FreezePacket
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * The ending's freeze on this client (FreezePacket): while it lasts, KeyboardInputMixin makes the
 * movement keys do nothing (looking around still works). It counts down on its own and ends after
 * FreezePacket.MAX_TICKS at most, on disconnecting, or when the server says so: never trapped,
 * even if the server stops answering.
 */
object ClientFreeze {

    @Volatile
    private var ticksLeft = 0

    @JvmStatic
    val frozen: Boolean get() = ticksLeft > 0

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(FreezePacket.TYPE) { packet, _, _ ->
            ticksLeft = packet.ticks.coerceIn(0, FreezePacket.MAX_TICKS)
        }
        // Counts even with the game paused: if anything, it ends sooner, never later.
        ClientTickEvents.END_CLIENT_TICK.register { if (ticksLeft > 0) ticksLeft-- }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ticksLeft = 0 }
    }
}
