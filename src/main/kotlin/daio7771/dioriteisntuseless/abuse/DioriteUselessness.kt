package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.network.AbuseStatePacket
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * "Diorite Is Useless" (fase A del final): para el jugador afectado, el hacha no tala y no puede
 * sacar cristales de diorita de la casilla de salida de un horno (el clic no hace nada; otros
 * jugadores y las tolvas sí pueden). Dura hasta "Start over", aunque se desactive el Abuse Mode
 * (desactivarlo no deshace lo hecho).
 *
 * El cliente también lo sabe (AbuseStatePacket) para que el clic en el horno no haga nada en su
 * pantalla, en vez de coger el cristal y que luego vuelva a su sitio.
 */
object DioriteUselessness {

    /** Valor que ha mandado el servidor a este cliente. Lo escribe el lado del cliente del mod. */
    @Volatile
    @JvmStatic
    var clientFlag = false

    @JvmStatic
    fun isUseless(player: Player): Boolean = try {
        if (player is ServerPlayer) {
            AbuseData.get(player.server).getIfPresent(player.uuid)?.dioriteUseless == true
        } else {
            clientFlag
        }
    } catch (e: Exception) {
        false  // regla de oro 1: ante la duda, la diorita sigue siendo útil
    }

    /** Para Slot.mayPickup en la salida de un horno. */
    @JvmStatic
    fun blocksFurnaceTake(player: Player, stack: ItemStack): Boolean =
        stack.`is`(ModItems.DIORITE_CRYSTAL) && isUseless(player)

    /** Manda el estado al cliente del jugador (al cambiar, al entrar y tras "Start over"). */
    fun sync(player: ServerPlayer) {
        if (!ServerPlayNetworking.canSend(player, AbuseStatePacket.TYPE)) return
        ServerPlayNetworking.send(player, AbuseStatePacket(isUseless(player)))
    }
}
