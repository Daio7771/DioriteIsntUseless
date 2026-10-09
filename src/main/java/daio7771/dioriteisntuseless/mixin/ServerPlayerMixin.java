package daio7771.dioriteisntuseless.mixin;

import daio7771.dioriteisntuseless.advancement.WornOutAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every statistic of a player goes through here. WornOutAdvancements picks the "dioritine axe or
 * pickaxe broken" ones to grant its advancements; vanilla statistics are not touched.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
    private void dioriteisntuseless$onStatAwarded(Stat<?> stat, int amount, CallbackInfo ci) {
        if (amount > 0) WornOutAdvancements.onStatAwarded((ServerPlayer) (Object) this, stat);
    }
}
