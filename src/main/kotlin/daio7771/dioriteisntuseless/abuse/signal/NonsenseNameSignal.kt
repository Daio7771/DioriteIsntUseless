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
 * Nombres sin sentido (nivel 2 en adelante): algunos ítems del inventario del jugador se le
 * muestran con nombres como "IJD72B90DB23". El servidor elige los ítems y el cliente
 * (client.abuse.NonsenseNames) hace el resto; el ítem nunca cambia.
 *
 * Nivel 2: un ítem y poco rato. Niveles 3 y 4: varios ítems y más rato.
 */
object NonsenseNameSignal : AbuseSignal {

    /** Ítems que nunca pierden su nombre (la diorita y sus variantes). Los del mod tampoco. */
    val KEEPS_ITS_NAME: TagKey<Item> = TagKey.create(Registries.ITEM, Dioriteisntuseless.id("keeps_its_name"))

    // Tiempo "a la vista" = ticks con un inventario o cofre abierto.
    private const val LEVEL_2_EXPOSURE = 5 * 20
    private const val LEVEL_2_LIFETIME = 5 * 60 * 20
    private const val LEVEL_3_EXPOSURE = 30 * 20
    private const val LEVEL_3_LIFETIME = 10 * 60 * 20
    private val LEVEL_3_ITEMS = 3..6

    override val id = "nonsense_names"
    override val minLevel = 2

    fun keepsItsName(item: Item): Boolean =
        BuiltInRegistries.ITEM.getKey(item).namespace == Dioriteisntuseless.MOD_ID ||
            BuiltInRegistries.ITEM.wrapAsHolder(item).`is`(KEEPS_ITS_NAME)

    override fun canRun(player: ServerPlayer, state: PlayerAbuse): Boolean =
        ServerPlayNetworking.canSend(player, NonsenseNamesPacket.TYPE) && candidates(player).isNotEmpty()

    override fun run(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val candidates = candidates(player).toMutableList()
        // Fisher-Yates con el azar del jugador.
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

    /** Quita todos los nombres sin sentido de ese jugador. */
    fun clear(player: ServerPlayer) {
        if (ServerPlayNetworking.canSend(player, NonsenseNamesPacket.TYPE)) {
            ServerPlayNetworking.send(player, NonsenseNamesPacket.clearAll())
        }
    }

    /**
     * Ítems distintos que lleva encima (inventario, armadura y mano secundaria) y que pueden
     * cambiar de nombre. Los que tienen nombre puesto en el yunque no: el yunque del cliente
     * podría acabar guardando el nombre sin sentido de verdad.
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
