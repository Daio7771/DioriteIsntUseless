package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.abuse.morse.MorseBeeper
import daio7771.dioriteisntuseless.abuse.signal.NonsenseNameSignal
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * "Start over" (HORROR_DESIGN.md, apartado 7): vuelta a empezar sin consecuencias.
 *
 * 1. Devuelve lo que se le quitó en la fase A (si no cabe, a sus pies). Sin conectar, al entrar.
 * 2. Deshace sus cambios en el mundo (WorldChanges), respetando lo que haya cambiado él.
 * 3. La diorita vuelve a ser útil para él.
 * 4. Quita los efectos visuales (nombres sin sentido, pitidos).
 * 5. Nivel, contadores y tiempo a 0: el ciclo puede volver a empezar.
 *
 * Funciona aunque el Abuse Mode esté desactivado (es la forma de deshacer) y aunque el jugador se
 * desconecte o el servidor se reinicie entre medias: lo pendiente está guardado con el mundo.
 */
object StartOver {

    fun run(server: MinecraftServer, uuid: UUID) {
        val data = AbuseData.get(server)
        val state = data.get(uuid)
        WorldChanges.undoAll(server, uuid)
        state.resetProgress()
        MorseBeeper.stop(uuid)
        val player = server.playerList.getPlayer(uuid)
        if (player != null) {
            NonsenseNameSignal.clear(player)
            returnItems(player, state)
        } else {
            state.returnItemsOnJoin = state.takenItems.isNotEmpty()
        }
        data.setDirty()
    }

    /** Al entrar un jugador: si hubo "Start over" sin él, se le devuelve lo suyo. */
    fun onJoin(player: ServerPlayer) {
        val data = AbuseData.get(player.server)
        val state = data.getIfPresent(player.uuid) ?: return
        if (!state.returnItemsOnJoin) return
        returnItems(player, state)
        data.setDirty()
    }

    private fun returnItems(player: ServerPlayer, state: PlayerAbuse) {
        // Se vacía la lista antes de dar nada: si algo fallara a medias, mejor perder un ítem
        // que duplicarlo al reintentar.
        val items = state.takenItems.toList()
        state.takenItems.clear()
        state.returnItemsOnJoin = false
        for (stack in items) {
            player.inventory.placeItemBackInInventory(stack)  // lo que no cabe, al suelo a sus pies
        }
    }
}
