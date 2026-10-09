package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * "Diorite Is Useless" (phase A of the ending): for the affected player, the axe does not fell,
 * the pickaxe does not mine 3x3 and they cannot take diorite crystals out of a furnace's output
 * slot (the click does nothing; other players and hoppers still can). It lasts until
 * "Start over", even if the Abuse Mode is disabled (disabling it does not undo what was done).
 *
 * The client knows too (AbuseStateSync) so the click on the furnace does nothing on their screen,
 * instead of picking up the crystal and having it jump back.
 */
object DioriteUselessness {

    /** Value the server sent to this client. Written by the client side of the mod. */
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
        false  // golden rule 1: when in doubt, diorite stays useful
    }

    /** For Slot.mayPickup on a furnace output. */
    @JvmStatic
    fun blocksFurnaceTake(player: Player, stack: ItemStack): Boolean =
        stack.`is`(ModItems.DIORITE_CRYSTAL) && isUseless(player)
}
