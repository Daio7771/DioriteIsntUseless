package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.abuse.morse.MorseBeeper
import daio7771.dioriteisntuseless.abuse.signal.NonsenseNameSignal
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * "Start over" (HORROR_DESIGN.md, section 7): starting again with no consequences.
 *
 * 1. Gives back what phase A took (if it does not fit, at their feet). If offline, on join.
 * 2. Undoes their changes to the world (WorldChanges), respecting whatever they changed themselves.
 * 3. Diorite is useful again for them.
 * 4. Removes the visual effects (nonsense names, beeps).
 * 5. Level, counters and time back to 0: the cycle can start again.
 *
 * It works even if the Abuse Mode is disabled (it is the way to undo things) and even if the player
 * disconnects or the server restarts in between: whatever is pending is saved with the world.
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
            AbuseStateSync.sync(player)
            returnItems(player, state)
        } else {
            state.returnItemsOnJoin = state.takenItems.isNotEmpty()
        }
        data.setDirty()
    }

    /** When a player joins: if "Start over" happened without them, their things are given back. */
    fun onJoin(player: ServerPlayer) {
        val data = AbuseData.get(player.server)
        val state = data.getIfPresent(player.uuid) ?: return
        if (!state.returnItemsOnJoin) return
        returnItems(player, state)
        data.setDirty()
    }

    private fun returnItems(player: ServerPlayer, state: PlayerAbuse) {
        // The list is emptied before giving anything: if something failed halfway, better to lose
        // an item than to duplicate it when retrying.
        val items = state.takenItems.toList()
        state.takenItems.clear()
        state.returnItemsOnJoin = false
        for (stack in items) {
            player.inventory.placeItemBackInInventory(stack)  // whatever does not fit drops at their feet
        }
    }
}
