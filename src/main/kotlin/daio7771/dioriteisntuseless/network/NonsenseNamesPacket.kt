package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Server -> client of the affected player: nonsense names (Abuse Mode, level 2 and up).
 * It is only visual: the client changes how the name of those items is shown, never the item.
 *
 * - [clear] = true: remove every nonsense name (on level change or with "Start over").
 * - [everything] = true (version 2): phase B of the ending, every item except diorite and the
 *   mod's own, until a [clear].
 * - Otherwise: show [items] with nonsense names until the player has had them on screen for
 *   [exposureTicks] (with an inventory open) or [lifetimeTicks] have passed.
 *
 * Format: [VERSION] and then the fields; future versions only add fields at the end.
 */
class NonsenseNamesPacket(
    val clear: Boolean,
    val items: List<ResourceLocation> = emptyList(),
    val exposureTicks: Int = 0,
    val lifetimeTicks: Int = 0,
    val everything: Boolean = false,
) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        // Version 1
        buf.writeBoolean(clear)
        buf.writeCollection(items, FriendlyByteBuf::writeResourceLocation)
        buf.writeVarInt(exposureTicks)
        buf.writeVarInt(lifetimeTicks)
        // Version 2
        buf.writeBoolean(everything)
    }

    override fun getType(): PacketType<NonsenseNamesPacket> = TYPE

    companion object {
        const val VERSION = 2

        /** Maximum initial capacity when reading: an absurd size does not reserve memory all at once. */
        private const val MAX_ITEMS = 4096

        val TYPE: PacketType<NonsenseNamesPacket> = PacketType.create(Dioriteisntuseless.id("nonsense_names"), ::read)

        fun clearAll() = NonsenseNamesPacket(clear = true)

        fun everything() = NonsenseNamesPacket(clear = false, everything = true)

        private fun read(buf: FriendlyByteBuf): NonsenseNamesPacket {
            val version = buf.readVarInt()
            val packet = NonsenseNamesPacket(
                clear = buf.readBoolean(),
                items = buf.readCollection({ size -> ArrayList(minOf(size, MAX_ITEMS)) }, FriendlyByteBuf::readResourceLocation),
                exposureTicks = buf.readVarInt(),
                lifetimeTicks = buf.readVarInt(),
                everything = version >= 2 && buf.readBoolean(),
            )
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
