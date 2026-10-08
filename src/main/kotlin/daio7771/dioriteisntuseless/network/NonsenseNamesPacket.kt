package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Servidor -> cliente del jugador afectado: nombres sin sentido (Abuse Mode, nivel 2 en adelante).
 * Es solo visual: el cliente cambia cómo se muestra el nombre de esos ítems, nunca el ítem.
 *
 * - [clear] = true: quitar todos los nombres sin sentido (al cambiar de nivel o con "Start over").
 * - Si no: mostrar [items] con nombres sin sentido hasta que el jugador los haya tenido a la vista
 *   [exposureTicks] (con un inventario abierto) o pasen [lifetimeTicks].
 *
 * Formato: [VERSION] y luego los campos; las versiones futuras solo añaden campos al final.
 */
class NonsenseNamesPacket(
    val clear: Boolean,
    val items: List<ResourceLocation> = emptyList(),
    val exposureTicks: Int = 0,
    val lifetimeTicks: Int = 0,
) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        buf.writeBoolean(clear)
        buf.writeCollection(items, FriendlyByteBuf::writeResourceLocation)
        buf.writeVarInt(exposureTicks)
        buf.writeVarInt(lifetimeTicks)
    }

    override fun getType(): PacketType<NonsenseNamesPacket> = TYPE

    companion object {
        const val VERSION = 1

        /** Capacidad inicial máxima al leer: un tamaño absurdo no reserva memoria de golpe. */
        private const val MAX_ITEMS = 4096

        val TYPE: PacketType<NonsenseNamesPacket> = PacketType.create(Dioriteisntuseless.id("nonsense_names"), ::read)

        fun clearAll() = NonsenseNamesPacket(clear = true)

        private fun read(buf: FriendlyByteBuf): NonsenseNamesPacket {
            buf.readVarInt()
            val packet = NonsenseNamesPacket(
                clear = buf.readBoolean(),
                items = buf.readCollection({ size -> ArrayList(minOf(size, MAX_ITEMS)) }, FriendlyByteBuf::readResourceLocation),
                exposureTicks = buf.readVarInt(),
                lifetimeTicks = buf.readVarInt(),
            )
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
