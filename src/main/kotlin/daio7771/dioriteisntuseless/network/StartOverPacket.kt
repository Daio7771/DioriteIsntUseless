package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Client -> server: the player pressed "Start over" in the credits. The server only accepts it if
 * the credits really are due (a client cannot start over whenever it wants).
 *
 * Format: [VERSION] and then the fields (none for now).
 */
class StartOverPacket : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
    }

    override fun getType(): PacketType<StartOverPacket> = TYPE

    companion object {
        const val VERSION = 1

        val TYPE: PacketType<StartOverPacket> = PacketType.create(Dioriteisntuseless.id("start_over"), ::read)

        private fun read(buf: FriendlyByteBuf): StartOverPacket {
            buf.skipBytes(buf.readableBytes())
            return StartOverPacket()
        }
    }
}
