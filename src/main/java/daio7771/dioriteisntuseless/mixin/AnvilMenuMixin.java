package daio7771.dioriteisntuseless.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import daio7771.dioriteisntuseless.item.DioritineAxeItem;
import daio7771.dioriteisntuseless.item.DioritineEnchantments;
import daio7771.dioriteisntuseless.item.DioritinePickaxeItem;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Anvil and dioritine tools:
 * - Books or combining tools: only the allowed enchantments are accepted.
 * - Repairing with dioritine ingots resets the axe's count of felled trees and the pickaxe's count
 *   of strikes (Mending does not).
 *
 * Extends ItemCombinerMenu only to be able to read inputSlots.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    @Shadow
    private int repairItemCountCost;

    private AnvilMenuMixin(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
    }

    /**
     * How much the first ingot repairs. Vanilla does not allow repairing a tool with no wear; here
     * it does, if the axe has felled trees or the pickaxe has strikes, so the count can be reset
     * even if Mending keeps it always full. It uses one ingot, like a normal repair.
     */
    @ModifyExpressionValue(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 0)
    )
    private int dioriteisntuseless$repairToResetTrees(int repairAmount) {
        if (repairAmount > 0) return repairAmount;
        ItemStack tool = this.inputSlots.getItem(0);
        return DioritineAxeItem.hasFelledTrees(tool) || DioritinePickaxeItem.hasStrikes(tool) ? 1 : repairAmount;
    }

    /** The result of a repair with ingots comes out with its count of trees or strikes at 0. */
    @ModifyArg(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"),
            index = 1
    )
    private ItemStack dioriteisntuseless$resetTreesOnRepair(ItemStack result) {
        // repairItemCountCost > 0 only after repairing with the material (not when combining or renaming).
        if (this.repairItemCountCost > 0) {
            DioritineAxeItem.resetFelledTrees(result);
            DioritinePickaxeItem.resetStrikes(result);
        }
        return result;
    }

    /**
     * An enchantment that is not allowed counts as "not applicable", just like Sharpness on a
     * pickaxe: if the book has nothing applicable, the anvil gives no result.
     */
    @WrapOperation(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private boolean dioriteisntuseless$onlyAllowedEnchantments(
            Enchantment enchantment,
            ItemStack stack,
            Operation<Boolean> original
    ) {
        return DioritineEnchantments.isAllowed(stack, enchantment) && original.call(enchantment, stack);
    }

    /**
     * Safety net: in creative, vanilla treats any enchantment as applicable right after
     * canEnchant. Here the ones that are not allowed are removed from what is written to the result.
     */
    @ModifyArg(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;setEnchantments(Ljava/util/Map;Lnet/minecraft/world/item/ItemStack;)V"),
            index = 0
    )
    private Map<Enchantment, Integer> dioriteisntuseless$stripDisallowed(Map<Enchantment, Integer> enchantments, ItemStack result) {
        if (DioritineEnchantments.isRestricted(result)) {
            enchantments.keySet().removeIf(enchantment -> !DioritineEnchantments.isAllowed(result, enchantment));
        }
        return enchantments;
    }
}
