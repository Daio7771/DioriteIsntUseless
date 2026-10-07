package daio7771.dioriteisntuseless.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * En vanilla, burn() copia el resultado completo si la casilla de salida está vacía, pero si
 * ya tiene ítems suma siempre 1 (output.grow(1)), aunque canBurn() sí tiene en cuenta el count
 * del resultado. Aquí se suma el count de la receta.
 *
 * Para recetas con count 1 (todas las vanilla) el comportamiento no cambia.
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
