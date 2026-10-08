package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Servidor -> cliente del jugador afectado: lo que su cliente necesita saber del Abuse Mode.
 * - [dioriteUseless]: fase A del final (ver DioriteUselessness).
 *
 * Formato: [VERSION] y luego los campos; las versiones futuras solo añaden campos al final.
 */
class AbuseStatePacket(val dioriteUseless: Boolean) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        buf.writeBoolean(dioriteUseless)
    }

    override fun getType(): PacketType<AbuseStatePacket> = TYPE

    companion object {
        const val VERSION = 1

        val TYPE: PacketType<AbuseStatePacket> = PacketType.create(Dioriteisntuseless.id("abuse_state"), ::read)

        private fun read(buf: FriendlyByteBuf): AbuseStatePacket {
            buf.readVarInt()
            val packet = AbuseStatePacket(dioriteUseless = buf.readBoolean())
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
