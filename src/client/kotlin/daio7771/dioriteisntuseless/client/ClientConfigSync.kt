package daio7771.dioriteisntuseless.client

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.block.DioriteStats
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.item.DioritineAxeItem
import daio7771.dioriteisntuseless.item.DioritinePickaxeItem
import daio7771.dioriteisntuseless.mixin.client.ItemAccessor
import daio7771.dioriteisntuseless.network.ConfigSyncPacket
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

/**
 * Lado del cliente de la sincronización. Conectado a un servidor remoto, el cliente usa los
 * valores del servidor (diorita y desgaste de las herramientas); al desconectarse vuelve a los suyos.
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
        LOGGER.info("Using the server's values: diorite {}, tool durability {}, trees before breaking {}, strikes before breaking {}.",
            packet.diorite, packet.axeDurability, packet.treesBeforeBreaking, packet.strikesBeforeBreaking)
        DioriteStats.applyServerValues(packet.diorite)
        setToolDurability(packet.axeDurability)
        DioritineAxeItem.serverTreesBeforeBreaking = packet.treesBeforeBreaking
        // 0: servidor de antes del pico (no lo tiene); se usa la configuración local.
        DioritinePickaxeItem.serverStrikesBeforeBreaking = packet.strikesBeforeBreaking.takeIf { it > 0 }
    }

    private fun useLocalValues() {
        DioriteStats.clearServerValues()
        setToolDurability(ModConfig.axeDurabilityAtStartup)
        DioritineAxeItem.serverTreesBeforeBreaking = null
        DioritinePickaxeItem.serverStrikesBeforeBreaking = null
    }

    /** El hacha y el pico son del mismo material: misma durabilidad. */
    private fun setToolDurability(durability: Int) {
        // Con 0 o menos dejarían de gastarse: un servidor así está mal, se ignora.
        if (durability <= 0) return
        for (tool in listOf(ModItems.DIORITINE_AXE, ModItems.DIORITINE_PICKAXE)) {
            if (tool.maxDamage != durability) (tool as ItemAccessor).`dioriteisntuseless$setMaxDamage`(durability)
        }
    }
}
