package daio7771.dioriteisntuseless.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import daio7771.dioriteisntuseless.item.DioritineEnchantments;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * /enchant sigue el mismo filtro: un encantamiento no permitido en el Dioritine Axe da el error
 * vanilla de "no admite ese encantamiento".
 */
@Mixin(EnchantCommand.class)
public abstract class EnchantCommandMixin {

    @WrapOperation(
            method = "enchant",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private static boolean dioriteisntuseless$onlyAllowedEnchantments(
            Enchantment enchantment,
            ItemStack stack,
            Operation<Boolean> original
    ) {
        return DioritineEnchantments.isAllowed(stack, enchantment) && original.call(enchantment, stack);
    }
}
