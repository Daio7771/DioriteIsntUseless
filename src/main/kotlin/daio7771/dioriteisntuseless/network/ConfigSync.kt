package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.config.ModConfig
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Lado del servidor de la sincronización: manda ConfigSyncPacket a cada jugador al entrar y a
 * todos después de cambiar la configuración. Llamar siempre desde el hilo del servidor.
 *
 * A un cliente sin el mod el paquete no le hace nada (lo ignora).
 */
object ConfigSync {

    fun init() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> send(handler.player) }
    }

    /** Reenvía los valores a todos los jugadores conectados (tras /diu reload, por ejemplo). */
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
        return ConfigSyncPacket(config.diorite, ModConfig.axeDurabilityAtStartup, config.treeFelling.treesBeforeBreaking)
    }
}
