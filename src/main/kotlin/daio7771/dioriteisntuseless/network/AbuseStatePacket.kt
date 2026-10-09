package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Server -> client of the affected player: what their client needs to know about the Abuse Mode.
 * - [dioriteUseless]: phase A of the ending (see DioriteUselessness).
 * - [creditsDue] (version 2): phase B is over; the credits open at the next calm moment.
 * - [level] (version 3): Abuse Mode level, from 0 to 5 (the ending); also 0 if it is disabled.
 *   It decides which background sound plays (AbuseAmbience).
 *
 * Format: [VERSION] and then the fields; future versions only add fields at the end.
 */
class AbuseStatePacket(val dioriteUseless: Boolean, val creditsDue: Boolean, val level: Int) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        // Version 1
        buf.writeBoolean(dioriteUseless)
        // Version 2
        buf.writeBoolean(creditsDue)
        // Version 3
        buf.writeVarInt(level)
    }

    override fun getType(): PacketType<AbuseStatePacket> = TYPE

    companion object {
        const val VERSION = 3

        val TYPE: PacketType<AbuseStatePacket> = PacketType.create(Dioriteisntuseless.id("abuse_state"), ::read)

        private fun read(buf: FriendlyByteBuf): AbuseStatePacket {
            val version = buf.readVarInt()
            val packet = AbuseStatePacket(
                dioriteUseless = buf.readBoolean(),
                creditsDue = version >= 2 && buf.readBoolean(),
                level = if (version >= 3) buf.readVarInt() else 0,
            )
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
