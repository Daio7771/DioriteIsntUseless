package daio7771.dioriteisntuseless.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import daio7771.dioriteisntuseless.client.abuse.NonsenseNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nombres sin sentido del Abuse Mode: solo cambia lo que se muestra en este cliente (ver
 * NonsenseNames). Sin nombres activos, devuelve el original tras un par de comprobaciones baratas.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
    private Component dioriteisntuseless$nonsenseName(Component original) {
        return NonsenseNames.displayName((ItemStack) (Object) this, original);
    }
}
