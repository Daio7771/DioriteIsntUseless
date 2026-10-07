package daio7771.dioriteisntuseless.registry

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.item.DioritineAxeItem
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item

object ModItems {

    val DIORITE_CRYSTAL: Item = register("diorite_crystal", Item(Item.Properties()))
    val DIORITINE_INGOT: Item = register("dioritine_ingot", Item(Item.Properties()))
    val DIORITINE_AXE: Item = register("dioritine_axe", DioritineAxeItem(Item.Properties()))

    private fun register(name: String, item: Item): Item =
        Registry.register(BuiltInRegistries.ITEM, Dioriteisntuseless.id(name), item)

    /**
     * Llamar desde onInitialize. Acceder al objeto ya registra los ítems de arriba
     * (antes de que se congelen los registros); aquí solo se añaden a las pestañas creativas.
     */
    fun init() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register { entries ->
            entries.accept(DIORITE_CRYSTAL)
            entries.accept(DIORITINE_INGOT)
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(DIORITINE_AXE)
        }
    }
}
