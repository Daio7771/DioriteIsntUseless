package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Servidor -> cliente del jugador afectado: lo que su cliente necesita saber del Abuse Mode.
 * - [dioriteUseless]: fase A del final (ver DioriteUselessness).
 * - [creditsDue] (versión 2): la fase B ha terminado; los créditos se abren en el próximo
 *   momento tranquilo.
 * - [level] (versión 3): nivel del Abuse Mode, de 0 a 5 (el final); 0 también si está
 *   desactivado. Decide el fondo que suena (AbuseAmbience).
 *
 * Formato: [VERSION] y luego los campos; las versiones futuras solo añaden campos al final.
 */
class AbuseStatePacket(val dioriteUseless: Boolean, val creditsDue: Boolean, val level: Int) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        // Versión 1
        buf.writeBoolean(dioriteUseless)
        // Versión 2
        buf.writeBoolean(creditsDue)
        // Versión 3
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
