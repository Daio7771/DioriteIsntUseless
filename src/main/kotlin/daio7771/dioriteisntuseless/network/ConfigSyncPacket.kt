package daio7771.dioriteisntuseless.network

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.config.DiuConfig
import net.fabricmc.fabric.api.networking.v1.FabricPacket
import net.fabricmc.fabric.api.networking.v1.PacketType
import net.minecraft.network.FriendlyByteBuf

/**
 * Servidor -> cliente: los valores de la configuración del servidor que el cliente también usa.
 *
 * - Diorita: el cliente calcula cuánto tarda en romperse un bloque con su propia dureza. Si no
 *   coincide con la del servidor, el bloque "se rompe" en pantalla y luego reaparece.
 * - Durabilidad del hacha (la de arranque del servidor): para que la barra del cliente cuadre.
 *
 * Formato: [VERSION] y luego los campos. Las versiones futuras solo pueden AÑADIR campos al final;
 * un cliente antiguo lee los que conoce e ignora el resto, y uno nuevo que reciba una versión
 * antigua usa sus valores locales para los campos que falten.
 */
class ConfigSyncPacket(
    val diorite: DiuConfig.Diorite,
    val axeDurability: Int,
) : FabricPacket {

    override fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(VERSION)
        // Versión 1
        buf.writeBoolean(diorite.enabled)
        buf.writeFloat(diorite.hardness)
        buf.writeFloat(diorite.blastResistance)
        buf.writeVarInt(axeDurability)
    }

    override fun getType(): PacketType<ConfigSyncPacket> = TYPE

    companion object {
        const val VERSION = 1

        val TYPE: PacketType<ConfigSyncPacket> = PacketType.create(Dioriteisntuseless.id("config_sync"), ::read)

        private fun read(buf: FriendlyByteBuf): ConfigSyncPacket {
            // La versión 1 es la primera: todo paquete válido trae al menos sus campos.
            buf.readVarInt()
            val packet = ConfigSyncPacket(
                diorite = DiuConfig.Diorite(
                    enabled = buf.readBoolean(),
                    hardness = buf.readFloat(),
                    blastResistance = buf.readFloat(),
                ),
                axeDurability = buf.readVarInt(),
            )
            // Campos de versiones más nuevas del mod que esta no conoce.
            buf.skipBytes(buf.readableBytes())
            return packet
        }
    }
}
