package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.network.NonsenseNamesPacket
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

/**
 * Nonsense names (level 2 and up): some items in the player's inventory are shown to them with
 * names like "IJD72B90DB23". The server picks the items and the client
 * (client.abuse.NonsenseNames) does the rest; the item itself never changes.
 *
 * Level 2: one item for a short while. Levels 3 and 4: several items for longer.
 */
object NonsenseNameSignal : AbuseSignal {

    /** Items that never lose their name (diorite and its variants). The mod's own items don't either. */
    val KEEPS_ITS_NAME: TagKey<Item> = TagKey.create(Registries.ITEM, Dioriteisntuseless.id("keeps_its_name"))

    // Time "on screen" = ticks with an inventory or chest open.
    private const val LEVEL_2_EXPOSURE = 5 * 20
    private const val LEVEL_2_LIFETIME = 5 * 60 * 20
    private const val LEVEL_3_EXPOSURE = 30 * 20
    private const val LEVEL_3_LIFETIME = 10 * 60 * 20
    private val LEVEL_3_ITEMS = 3..6

    override val id = "nonsense_names"
    override val minLevel = 2

    /** At the ending every item is already renamed ([everything]). */
    override val maxLevel = 4

    fun keepsItsName(item: Item): Boolean =
        BuiltInRegistries.ITEM.getKey(item).namespace == Dioriteisntuseless.MOD_ID ||
            BuiltInRegistries.ITEM.wrapAsHolder(item).`is`(KEEPS_ITS_NAME)

    override fun canRun(player: ServerPlayer, state: PlayerAbuse): Boolean =
        ServerPlayNetworking.canSend(player, NonsenseNamesPacket.TYPE) && candidates(player).isNotEmpty()

    override fun run(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val candidates = candidates(player).toMutableList()
        // Fisher-Yates with the player's randomness.
        for (i in candidates.lastIndex downTo 1) {
            val j = player.random.nextInt(i + 1)
            candidates[i] = candidates[j].also { candidates[j] = candidates[i] }
        }
        val packet = if (state.level <= 2) {
            NonsenseNamesPacket(false, candidates.take(1).map(::key), LEVEL_2_EXPOSURE, LEVEL_2_LIFETIME)
        } else {
            val count = LEVEL_3_ITEMS.first + player.random.nextInt(LEVEL_3_ITEMS.last - LEVEL_3_ITEMS.first + 1)
            NonsenseNamesPacket(false, candidates.take(count).map(::key), LEVEL_3_EXPOSURE, LEVEL_3_LIFETIME)
        }
        ServerPlayNetworking.send(player, packet)
        return true
    }

    /** Phase B of the ending: every item (except diorite and the mod's own) until "Start over". */
    fun everything(player: ServerPlayer) {
        if (ServerPlayNetworking.canSend(player, NonsenseNamesPacket.TYPE)) {
            ServerPlayNetworking.send(player, NonsenseNamesPacket.everything())
        }
    }

    /** Removes every nonsense name of that player. */
    fun clear(player: ServerPlayer) {
        if (ServerPlayNetworking.canSend(player, NonsenseNamesPacket.TYPE)) {
            ServerPlayNetworking.send(player, NonsenseNamesPacket.clearAll())
        }
    }

    /**
     * Distinct items they carry (inventory, armor and offhand) that can be renamed. Not the ones
     * renamed on an anvil: the client's anvil could end up saving the nonsense name for real.
     */
    private fun candidates(player: ServerPlayer): Set<Item> {
        val inventory = player.inventory
        val items = LinkedHashSet<Item>()
        for (stack in inventory.items + inventory.armor + inventory.offhand) {
            if (!stack.isEmpty && !stack.hasCustomHoverName() && !keepsItsName(stack.item)) items += stack.item
        }
        return items
    }

    private fun key(item: Item) = BuiltInRegistries.ITEM.getKey(item)
}
