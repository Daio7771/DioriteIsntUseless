package daio7771.dioriteisntuseless.mixin;

import daio7771.dioriteisntuseless.abuse.DioriteUselessness;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase A of the ending: the affected player cannot take diorite crystals out of a furnace output
 * (not with a click, Shift+click, number keys or by throwing them: it all goes through mayPickup).
 * FurnaceResultSlot does not override mayPickup, so it is checked here; every other slot only
 * pays for an instanceof.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow
    public abstract ItemStack getItem();

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void dioriteisntuseless$noCrystalsForTheAbuser(Player player, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof FurnaceResultSlot && DioriteUselessness.blocksFurnaceTake(player, this.getItem())) {
            cir.setReturnValue(false);
        }
    }
}
