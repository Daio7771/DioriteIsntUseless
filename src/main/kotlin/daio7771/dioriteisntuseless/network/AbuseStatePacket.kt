package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Servidor -> cliente del jugador afectado: lo que su cliente necesita saber del Abuse Mode.
 * - [dioriteUseless]: fase A del final (ver DioriteUselessness).
 * - [creditsDue] (versión 2): la fase B ha terminado; los créditos se abren en el próximo
 *   momento tranquilo (al dormir o al abrir el menú de pausa).
 *
 * Formato: [VERSION] y luego los campos; las versiones futuras solo añaden campos al final.
 */
class AbuseStatePacket(val dioriteUseless: Boolean, val creditsDue: Boolean) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        // Versión 1
        buf.writeBoolean(dioriteUseless)
        // Versión 2
        buf.writeBoolean(creditsDue)
    }

    override fun getType(): PacketType<AbuseStatePacket> = TYPE

    companion object {
        const val VERSION = 2

        val TYPE: PacketType<AbuseStatePacket> = PacketType.create(Dioriteisntuseless.id("abuse_state"), ::read)

        private fun read(buf: FriendlyByteBuf): AbuseStatePacket {
            val version = buf.readVarInt()
            val packet = AbuseStatePacket(
                dioriteUseless = buf.readBoolean(),
                creditsDue = version >= 2 && buf.readBoolean(),
            )
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
