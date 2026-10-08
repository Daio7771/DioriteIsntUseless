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
 * Fase A del final: el jugador afectado no puede sacar cristales de diorita de la salida de un
 * horno (ni con clic, ni Shift+clic, ni teclas numéricas, ni tirándolos: todo pasa por mayPickup).
 * FurnaceResultSlot no redefine mayPickup, así que se comprueba aquí; las demás casillas solo
 * pagan un instanceof.
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
