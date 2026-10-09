package daio7771.dioriteisntuseless.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import daio7771.dioriteisntuseless.client.abuse.NonsenseNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Abuse Mode nonsense names: it only changes what this client shows (see NonsenseNames). With no
 * active names, it returns the original after a couple of cheap checks.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
    private Component dioriteisntuseless$nonsenseName(Component original) {
        return NonsenseNames.displayName((ItemStack) (Object) this, original);
    }
}
