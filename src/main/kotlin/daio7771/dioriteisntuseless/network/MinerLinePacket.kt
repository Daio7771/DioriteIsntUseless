package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.abuse.visitor.MinerSpeech
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component

/**
 * Server -> client of the visited player: The Miner (entity [minerId]) says [line] in [mood]. The
 * client shows it in a bubble above his head, letter by letter with his voice, and then copies it
 * to the chat (MinerBubbles). Clients without the mod get the line in chat instead (MinerSpeech).
 *
 * Format: [VERSION] and then the fields; future versions only add fields at the end.
 */
class MinerLinePacket(val minerId: Int, val line: Component, val mood: MinerSpeech.Mood) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        buf.writeVarInt(minerId)
        buf.writeComponent(line)
        buf.writeByte(mood.ordinal)
    }

    override fun getType(): PacketType<MinerLinePacket> = TYPE

    companion object {
        const val VERSION = 1

        val TYPE: PacketType<MinerLinePacket> = PacketType.create(Dioriteisntuseless.id("miner_line"), ::read)

        private fun read(buf: FriendlyByteBuf): MinerLinePacket {
            buf.readVarInt()  // version
            val minerId = buf.readVarInt()
            val line = buf.readComponent()
            val mood = MinerSpeech.Mood.entries.getOrElse(buf.readByte().toInt()) { MinerSpeech.Mood.CALM }
            buf.skipBytes(buf.readableBytes())
            return MinerLinePacket(minerId, line, mood)
        }
    }
}
