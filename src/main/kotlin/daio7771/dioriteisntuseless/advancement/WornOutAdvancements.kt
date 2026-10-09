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
 * Advancements for wearing out a dioritine tool until it breaks, the first time:
 * - "Diorite Isn't Useless!" (advancements/wear_out_axe.json): the axe. The mod congratulates the
 *   player; it is the first thing it does on its own, and there is nothing odd about it.
 * - "Rock Solid Choice" (advancements/wear_out_pickaxe.json): the pickaxe. It hangs from the
 *   previous one, but can be earned without it.
 *
 * They are detected through the "item broken" statistic, which covers every way of breaking them
 * (by durability or by reaching the limit of trees or strikes). The advancements' criterion is
 * "minecraft:impossible": only this code grants them. "Start over" does not take them away.
 */
object WornOutAdvancements {

    /** An advancement and its only criterion (the criterion name is in saved progress: do not change it). */
    private class WornOut(val id: ResourceLocation, val criterion: String)

    private val ADVANCEMENTS: Map<Item, WornOut> by lazy {
        mapOf(
            ModItems.DIORITINE_AXE to WornOut(Dioriteisntuseless.id("wear_out_axe"), "wore_out_axe"),
            ModItems.DIORITINE_PICKAXE to WornOut(Dioriteisntuseless.id("wear_out_pickaxe"), "wore_out_pickaxe"),
        )
    }

    /** Called by ServerPlayerMixin with every statistic the player is awarded. */
    @JvmStatic
    fun onStatAwarded(player: ServerPlayer, stat: Stat<*>) {
        if (stat.type != Stats.ITEM_BROKEN) return
        val wornOut = (stat.value as? Item)?.let(ADVANCEMENTS::get) ?: return
        try {
            // Null if a datapack removed it. If they already have it, award does nothing.
            val advancement = player.server.advancements.getAdvancement(wornOut.id) ?: return
            player.advancements.award(advancement, wornOut.criterion)
        } catch (e: Exception) {
            // Better no advancement than no game: this is called from inside vanilla.
            LOGGER.error("Could not award the {} advancement.", wornOut.id, e)
        }
    }
}
