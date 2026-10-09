package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.config.ModConfig
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Server side of the sync: sends ConfigSyncPacket to each player on join and to everyone after
 * the config changes. Always call from the server thread.
 *
 * The packet does nothing to a client without the mod (it ignores it).
 */
object ConfigSync {

    fun init() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> send(handler.player) }
    }

    /** Sends the values again to every connected player (after /diu reload, for example). */
    fun broadcast(server: MinecraftServer) {
        val packet = packet()
        for (player in PlayerLookup.all(server)) {
            ServerPlayNetworking.send(player, packet)
        }
    }

    private fun send(player: ServerPlayer) {
        ServerPlayNetworking.send(player, packet())
    }

    private fun packet(): ConfigSyncPacket {
        val config = ModConfig.current
        return ConfigSyncPacket(
            config.diorite, ModConfig.axeDurabilityAtStartup,
            config.treeFelling.treesBeforeBreaking, config.pickaxe.strikesBeforeBreaking,
        )
    }
}
