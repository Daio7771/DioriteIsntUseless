package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component

/**
 * Server -> client of the visited player, right before The Miner's trading screen opens: the note
 * at the top of that screen, with what he wants right now (MinerTradeNote draws it).
 *
 * - [header] (may be empty) and [body]: his job, or his phrase of the level if he has no job.
 * - With a job: [amount] and [progress] (0 amount: no progress line) and what it pays, [reward].
 *   With [liveCrystals], the progress is the Diorite Crystals they carry, counted by the client
 *   while the screen is open (selling him crystals changes it).
 *
 * Format: [VERSION] and then the fields; future versions only add fields at the end.
 */
class MinerNotePacket(
    val header: Component,
    val body: Component,
    val amount: Int,
    val progress: Int,
    val liveCrystals: Boolean,
    val reward: Component,
) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        buf.writeComponent(header)
        buf.writeComponent(body)
        buf.writeVarInt(amount)
        buf.writeVarInt(progress)
        buf.writeBoolean(liveCrystals)
        buf.writeComponent(reward)
    }

    override fun getType(): PacketType<MinerNotePacket> = TYPE

    companion object {
        const val VERSION = 1

        val TYPE: PacketType<MinerNotePacket> = PacketType.create(Dioriteisntuseless.id("miner_note"), ::read)

        private fun read(buf: FriendlyByteBuf): MinerNotePacket {
            buf.readVarInt()  // version
            val packet = MinerNotePacket(
                header = buf.readComponent(),
                body = buf.readComponent(),
                amount = buf.readVarInt().coerceAtLeast(0),
                progress = buf.readVarInt().coerceAtLeast(0),
                liveCrystals = buf.readBoolean(),
                reward = buf.readComponent(),
            )
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
