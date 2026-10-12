package daio7771.dioriteisntuseless.mixin.client;

import daio7771.dioriteisntuseless.client.abuse.ClientFreeze;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During the ending's freeze (ClientFreeze), the movement keys read as not pressed: no walking,
 * jumping or sneaking. The mouse is not touched, so they can still look around.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void dioriteisntuseless$freeze(boolean isSneaking, float sneakingSpeedMultiplier, CallbackInfo ci) {
        if (!ClientFreeze.getFrozen()) return;
        this.up = false;
        this.down = false;
        this.left = false;
        this.right = false;
        this.jumping = false;
        this.shiftKeyDown = false;
        this.forwardImpulse = 0.0F;
        this.leftImpulse = 0.0F;
    }
}
