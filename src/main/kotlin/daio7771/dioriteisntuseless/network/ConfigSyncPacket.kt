package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.config.DiuConfig
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Server -> client: the server config values the client uses too.
 *
 * - Diorite: the client works out how long a block takes to break with its own hardness. If it
 *   does not match the server's, the block "breaks" on screen and then comes back.
 * - Durability of the axe and the pickaxe (the server's startup value): so the client's bar
 *   matches.
 * - Trees before the axe breaks (version 2) and strikes before the pickaxe breaks (version 3):
 *   also for the bar.
 *
 * Format: [VERSION] and then the fields. Future versions may only ADD fields at the end; an old
 * client reads the ones it knows and ignores the rest, and a new one that receives an old version
 * uses its local values for the missing fields.
 */
class ConfigSyncPacket(
    val diorite: DiuConfig.Diorite,
    val axeDurability: Int,
    val treesBeforeBreaking: Int,
    val strikesBeforeBreaking: Int,
) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        // Version 1
        buf.writeBoolean(diorite.enabled)
        buf.writeFloat(diorite.hardness)
        buf.writeFloat(diorite.blastResistance)
        buf.writeVarInt(axeDurability)
        // Version 2
        buf.writeVarInt(treesBeforeBreaking)
        // Version 3
        buf.writeVarInt(strikesBeforeBreaking)
    }

    override fun getType(): PacketType<ConfigSyncPacket> = TYPE

    companion object {
        const val VERSION = 3

        val TYPE: PacketType<ConfigSyncPacket> = PacketType.create(Dioriteisntuseless.id("config_sync"), ::read)

        private fun read(buf: FriendlyByteBuf): ConfigSyncPacket {
            // Version 1 is the first one: every valid packet has at least its fields.
            val version = buf.readVarInt()
            val packet = ConfigSyncPacket(
                diorite = DiuConfig.Diorite(
                    enabled = buf.readBoolean(),
                    hardness = buf.readFloat(),
                    blastResistance = buf.readFloat(),
                ),
                axeDurability = buf.readVarInt(),
                // A version 1 server did not break the axe by trees: no limit.
                treesBeforeBreaking = if (version >= 2) buf.readVarInt() else 0,
                // A server without the pickaxe does not have it: the bar uses the local config.
                strikesBeforeBreaking = if (version >= 3) buf.readVarInt() else 0,
            )
            // Fields from newer versions of the mod that this one does not know.
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
