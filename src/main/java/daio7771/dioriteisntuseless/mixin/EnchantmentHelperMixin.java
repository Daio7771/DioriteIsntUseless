package daio7771.dioriteisntuseless.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import daio7771.dioriteisntuseless.item.DioritineEnchantments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mesa de encantar: getAvailableEnchantmentResults es la lista de la que la mesa elige sus
 * ofertas (filtra por categoría, no por Enchantment.canEnchant). Para el Dioritine Axe se dejan
 * solo los permitidos; como Mending es un tesoro y la mesa nunca lo ofrece, queda Efficiency.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @ModifyReturnValue(method = "getAvailableEnchantmentResults", at = @At("RETURN"))
    private static List<EnchantmentInstance> dioriteisntuseless$filterTableOffers(
            List<EnchantmentInstance> original,
            @Local(argsOnly = true) ItemStack stack
    ) {
        if (!DioritineEnchantments.isRestricted(stack)) {
            return original;
        }
        List<EnchantmentInstance> filtered = new ArrayList<>(original);
        filtered.removeIf(instance -> !DioritineEnchantments.isAllowed(stack, instance.enchantment));
        return filtered;
    }
}
