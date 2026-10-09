package daio7771.dioriteisntuseless.registry

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.item.DioritineAxeItem
import daio7771.dioriteisntuseless.item.DioritinePickaxeItem
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item

object ModItems {

    val DIORITE_CRYSTAL: Item = register("diorite_crystal", Item(Item.Properties()))
    val DIORITINE_INGOT: Item = register("dioritine_ingot", Item(Item.Properties()))
    val DIORITINE_AXE: Item = register("dioritine_axe", DioritineAxeItem(Item.Properties()))
    val DIORITINE_PICKAXE: Item = register("dioritine_pickaxe", DioritinePickaxeItem(Item.Properties()))

    private fun register(name: String, item: Item): Item =
        Registry.register(BuiltInRegistries.ITEM, Dioriteisntuseless.id(name), item)

    /**
     * Call from onInitialize. Accessing the object already registers the items above (before
     * the registries are frozen); here they are only added to the creative tabs.
     */
    fun init() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register { entries ->
            entries.accept(DIORITE_CRYSTAL)
            entries.accept(DIORITINE_INGOT)
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(DIORITINE_AXE)
            entries.accept(DIORITINE_PICKAXE)
        }
    }
}
