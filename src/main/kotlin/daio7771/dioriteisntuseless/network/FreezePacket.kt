package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Server -> client of the affected player: during the ending (Finale), their movement keys do
 * nothing for [ticks] ticks; 0 gives them back right away. The client never freezes for longer than
 * [MAX_TICKS], whatever it receives, and unfreezes on its own when the time is up, even if the
 * server never says so.
 *
 * Format: [VERSION] and then the fields; future versions only add fields at the end.
 */
class FreezePacket(val ticks: Int) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        buf.writeVarInt(ticks)
    }

    override fun getType(): PacketType<FreezePacket> = TYPE

    companion object {
        const val VERSION = 1

        /** 15 seconds: golden rule, nobody can ever be trapped. */
        const val MAX_TICKS = 15 * 20

        val TYPE: PacketType<FreezePacket> = PacketType.create(Dioriteisntuseless.id("freeze"), ::read)

        private fun read(buf: FriendlyByteBuf): FreezePacket {
            buf.readVarInt()  // version
            val packet = FreezePacket(buf.readVarInt().coerceIn(0, MAX_TICKS))
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
