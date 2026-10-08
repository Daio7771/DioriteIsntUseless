package daio7771.dioriteisntuseless.advancement

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stat
import net.minecraft.stats.Stats

/**
 * Logro "Diorite Isn't Useless!" (data/dioriteisntuseless/advancements/wear_out_axe.json): la
 * primera vez que el jugador gasta un hacha de dioritina hasta romperla, el mod le felicita. Es lo
 * primero que hace el mod por su cuenta, y no tiene nada de raro.
 *
 * Se detecta con la estadística "objeto roto", que cubre todas las formas de romperla (por
 * durabilidad o por llegar a treeFelling.treesBeforeBreaking). El criterio del logro es
 * "minecraft:impossible": solo lo concede este código.
 */
object AxeWornOutAdvancement {

    private val ID = ResourceLocation(Dioriteisntuseless.MOD_ID, "wear_out_axe")
    private const val CRITERION = "wore_out_axe"

    /** Lo llama ServerPlayerMixin con cada estadística que recibe el jugador. */
    @JvmStatic
    fun onStatAwarded(player: ServerPlayer, stat: Stat<*>) {
        if (stat.type != Stats.ITEM_BROKEN || stat.value != ModItems.DIORITINE_AXE) return
        try {
            // Null si un datapack lo ha quitado. Si ya lo tiene, award no hace nada.
            val advancement = player.server.advancements.getAdvancement(ID) ?: return
            player.advancements.award(advancement, CRITERION)
        } catch (e: Exception) {
            // Sin logro antes que sin juego: esto se llama desde dentro de vanilla.
            LOGGER.error("Could not award the {} advancement.", ID, e)
        }
    }
}
