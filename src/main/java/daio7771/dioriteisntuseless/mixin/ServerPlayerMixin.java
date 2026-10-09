package daio7771.dioriteisntuseless.mixin;

import daio7771.dioriteisntuseless.advancement.WornOutAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Todas las estadísticas de un jugador pasan por aquí. WornOutAdvancements se queda con las de
 * "hacha o pico de dioritina roto" para dar sus logros; las estadísticas vanilla no se tocan.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
    private void dioriteisntuseless$onStatAwarded(Stat<?> stat, int amount, CallbackInfo ci) {
        if (amount > 0) WornOutAdvancements.onStatAwarded((ServerPlayer) (Object) this, stat);
    }
}
