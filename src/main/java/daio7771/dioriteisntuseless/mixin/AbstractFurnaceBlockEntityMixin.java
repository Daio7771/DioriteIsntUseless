package daio7771.dioriteisntuseless.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * In vanilla, burn() copies the whole result if the output slot is empty, but if it already has
 * items it always adds 1 (output.grow(1)), even though canBurn() does take the result's count into
 * account. Here the recipe's count is added.
 *
 * For recipes with a count of 1 (every vanilla one) the behavior does not change.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    @ModifyArg(
            method = "burn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;grow(I)V")
    )
    private static int dioriteisntuseless$growByResultCount(
            int amount,
            @Local(argsOnly = true) RegistryAccess registryAccess,
            @Local(argsOnly = true) Recipe<?> recipe
    ) {
        return recipe.getResultItem(registryAccess).getCount();
    }
}
