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
 * Yunque y herramientas de dioritina:
 * - Libros o combinar herramientas: solo acepta los encantamientos permitidos.
 * - Reparar con lingotes de dioritina reinicia la cuenta de árboles talados del hacha y la de
 *   picadas del pico (Mending no).
 *
 * Hereda de ItemCombinerMenu solo para poder leer inputSlots.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    @Shadow
    private int repairItemCountCost;

    private AnvilMenuMixin(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
    }

    /**
     * Cuánto repara el primer lingote. Vanilla no deja reparar una herramienta sin desgaste;
     * aquí sí, si el hacha lleva árboles talados o el pico picadas, para que la cuenta se pueda
     * reiniciar aunque Mending la tenga siempre a tope. Gasta un lingote, como una reparación normal.
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

    /** El resultado de una reparación con lingotes sale con la cuenta de árboles o de picadas a 0. */
    @ModifyArg(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"),
            index = 1
    )
    private ItemStack dioriteisntuseless$resetTreesOnRepair(ItemStack result) {
        // repairItemCountCost > 0 solo tras reparar con el material (no al combinar ni renombrar).
        if (this.repairItemCountCost > 0) {
            DioritineAxeItem.resetFelledTrees(result);
            DioritinePickaxeItem.resetStrikes(result);
        }
        return result;
    }

    /**
     * Un encantamiento no permitido cuenta como "no aplicable", igual que Sharpness en un pico:
     * si el libro no trae nada aplicable, el yunque no da resultado.
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
     * Red de seguridad: en creativo vanilla da por aplicable cualquier encantamiento justo después
     * de canEnchant. Aquí se quitan los no permitidos de lo que se escribe en el resultado.
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
