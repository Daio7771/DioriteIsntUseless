package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Cliente -> servidor: el jugador ha pulsado "Start over" en los créditos. El servidor solo lo
 * acepta si de verdad le tocan los créditos (un cliente no puede reiniciarse cuando quiera).
 *
 * Formato: [VERSION] y luego los campos (ahora ninguno).
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
