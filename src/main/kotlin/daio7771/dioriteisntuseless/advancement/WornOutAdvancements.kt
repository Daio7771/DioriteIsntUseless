package daio7771.dioriteisntuseless.advancement

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stat
import net.minecraft.stats.Stats
import net.minecraft.world.item.Item

/**
 * Logros de gastar una herramienta de dioritina hasta romperla, la primera vez:
 * - "Diorite Isn't Useless!" (advancements/wear_out_axe.json): el hacha. El mod le felicita; es
 *   lo primero que hace por su cuenta, y no tiene nada de raro.
 * - "Rock Solid Choice" (advancements/wear_out_pickaxe.json): el pico. Cuelga del anterior, pero
 *   se puede conseguir sin él.
 *
 * Se detectan con la estadística "objeto roto", que cubre todas las formas de romperlas (por
 * durabilidad o por llegar al límite de árboles o de picadas). El criterio de los logros es
 * "minecraft:impossible": solo los concede este código. "Start over" no los quita.
 */
object WornOutAdvancements {

    /** Un logro y su único criterio (el nombre del criterio va en el progreso guardado: no cambiarlo). */
    private class WornOut(val id: ResourceLocation, val criterion: String)

    private val ADVANCEMENTS: Map<Item, WornOut> by lazy {
        mapOf(
            ModItems.DIORITINE_AXE to WornOut(Dioriteisntuseless.id("wear_out_axe"), "wore_out_axe"),
            ModItems.DIORITINE_PICKAXE to WornOut(Dioriteisntuseless.id("wear_out_pickaxe"), "wore_out_pickaxe"),
        )
    }

    /** Lo llama ServerPlayerMixin con cada estadística que recibe el jugador. */
    @JvmStatic
    fun onStatAwarded(player: ServerPlayer, stat: Stat<*>) {
        if (stat.type != Stats.ITEM_BROKEN) return
        val wornOut = (stat.value as? Item)?.let(ADVANCEMENTS::get) ?: return
        try {
            // Null si un datapack lo ha quitado. Si ya lo tiene, award no hace nada.
            val advancement = player.server.advancements.getAdvancement(wornOut.id) ?: return
            player.advancements.award(advancement, wornOut.criterion)
        } catch (e: Exception) {
            // Sin logro antes que sin juego: esto se llama desde dentro de vanilla.
            LOGGER.error("Could not award the {} advancement.", wornOut.id, e)
        }
    }
}
