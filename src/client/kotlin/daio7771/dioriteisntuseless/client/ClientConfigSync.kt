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
 * Client side of the sync. Connected to a remote server, the client uses the server's values
 * (diorite and tool wear); on disconnecting it goes back to its own.
 *
 * In single player (and on the host of a LAN game) nothing is touched: the client and the
 * integrated server share the same config and the same objects, so they already match.
 * Everything runs on the client thread.
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
        // A server with the mod sends its values right after joining. Until then, and forever on a
        // server without the mod, diorite is the vanilla one, which is what the server uses.
        DioriteStats.applyServerValues(DiuConfig.Diorite.VANILLA)
    }

    private fun onServerValues(packet: ConfigSyncPacket) {
        if (Minecraft.getInstance().hasSingleplayerServer()) return
        // One line on join and another per /diu reload: useful if someone sees blocks coming back.
        LOGGER.info("Using the server's values: diorite {}, tool durability {}, trees before breaking {}, strikes before breaking {}.",
            packet.diorite, packet.axeDurability, packet.treesBeforeBreaking, packet.strikesBeforeBreaking)
        DioriteStats.applyServerValues(packet.diorite)
        setToolDurability(packet.axeDurability)
        DioritineAxeItem.serverTreesBeforeBreaking = packet.treesBeforeBreaking
        // 0: a server from before the pickaxe (it does not have it); the local config is used.
        DioritinePickaxeItem.serverStrikesBeforeBreaking = packet.strikesBeforeBreaking.takeIf { it > 0 }
    }

    private fun useLocalValues() {
        DioriteStats.clearServerValues()
        setToolDurability(ModConfig.axeDurabilityAtStartup)
        DioritineAxeItem.serverTreesBeforeBreaking = null
        DioritinePickaxeItem.serverStrikesBeforeBreaking = null
    }

    /** The axe and the pickaxe are made of the same material: same durability. */
    private fun setToolDurability(durability: Int) {
        // With 0 or less they would stop wearing out: such a server is wrong, so it is ignored.
        if (durability <= 0) return
        for (tool in listOf(ModItems.DIORITINE_AXE, ModItems.DIORITINE_PICKAXE)) {
            if (tool.maxDamage != durability) (tool as ItemAccessor).`dioriteisntuseless$setMaxDamage`(durability)
        }
    }
}
