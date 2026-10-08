package daio7771.dioriteisntuseless.client

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.block.DioriteStats
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.item.DioritineAxeItem
import daio7771.dioriteisntuseless.mixin.client.ItemAccessor
import daio7771.dioriteisntuseless.network.ConfigSyncPacket
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

/**
 * Lado del cliente de la sincronización. Conectado a un servidor remoto, el cliente usa los
 * valores del servidor (diorita y durabilidad del hacha); al desconectarse vuelve a los suyos.
 *
 * En un solo jugador (y en el anfitrión de una partida LAN) no se toca nada: cliente y servidor
 * integrado comparten la misma configuración y los mismos objetos, así que ya coinciden.
 * Todo se ejecuta en el hilo del cliente.
 */
object ClientConfigSync {

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPacket.TYPE) { packet, _, _ -> onServerValues(packet) }
        ClientPlayConnectionEvents.JOIN.register { _, _, client -> onJoin(client) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> useLocalValues() }
    }

    private fun onJoin(client: Minecraft) {
        if (client.hasSingleplayerServer()) {
            useLocalValues()
            return
        }
        // Un servidor con el mod manda sus valores justo después de entrar. Hasta entonces, y para
        // siempre en un servidor sin el mod, la diorita es la vanilla, que es lo que usa el servidor.
        DioriteStats.applyServerValues(DiuConfig.Diorite.VANILLA)
    }

    private fun onServerValues(packet: ConfigSyncPacket) {
        if (Minecraft.getInstance().hasSingleplayerServer()) return
        // Una línea al entrar y otra por /diu reload: útil si alguien ve bloques que reaparecen.
        LOGGER.info("Using the server's values: diorite {}, axe durability {}, trees before breaking {}.",
            packet.diorite, packet.axeDurability, packet.treesBeforeBreaking)
        DioriteStats.applyServerValues(packet.diorite)
        setAxeDurability(packet.axeDurability)
        DioritineAxeItem.serverTreesBeforeBreaking = packet.treesBeforeBreaking
    }

    private fun useLocalValues() {
        DioriteStats.clearServerValues()
        setAxeDurability(ModConfig.axeDurabilityAtStartup)
        DioritineAxeItem.serverTreesBeforeBreaking = null
    }

    private fun setAxeDurability(durability: Int) {
        val axe = ModItems.DIORITINE_AXE
        // Con 0 o menos el hacha dejaría de gastarse: un servidor así está mal, se ignora.
        if (durability <= 0 || axe.maxDamage == durability) return
        (axe as ItemAccessor).`dioriteisntuseless$setMaxDamage`(durability)
    }
}
