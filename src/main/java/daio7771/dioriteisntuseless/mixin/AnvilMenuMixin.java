package daio7771.dioriteisntuseless.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import daio7771.dioriteisntuseless.item.DioritineEnchantments;
import java.util.Map;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Yunque (libros o combinar hachas): el Dioritine Axe solo acepta los encantamientos permitidos.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

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
