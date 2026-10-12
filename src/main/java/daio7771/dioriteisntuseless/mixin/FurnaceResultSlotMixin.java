package daio7771.dioriteisntuseless.mixin;

import daio7771.dioriteisntuseless.abuse.visitor.MinerVisits;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player has taken something out of a furnace or blast furnace output (click, Shift+click,
 * number keys or throwing it: every way goes through checkTakeAchievements). The first Diorite
 * Crystals bring The Miner. Hoppers do not count: there is no player.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

    @Shadow
    @Final
    private Player player;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void dioriteisntuseless$onTaken(ItemStack stack, CallbackInfo ci) {
        MinerVisits.onFurnaceTake(this.player, stack);
    }
}
