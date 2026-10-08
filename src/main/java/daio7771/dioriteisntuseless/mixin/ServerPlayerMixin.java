package daio7771.dioriteisntuseless.mixin;

import daio7771.dioriteisntuseless.abuse.AbuseTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Todas las estadísticas de un jugador pasan por aquí. El Abuse Mode cuenta las que le interesan
 * (bloques minados y hachas fabricadas) con sus propios contadores; las estadísticas vanilla no
 * se tocan. AbuseTracker descarta enseguida todo lo demás.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
    private void dioriteisntuseless$countAbuse(Stat<?> stat, int amount, CallbackInfo ci) {
        AbuseTracker.onStatAwarded((ServerPlayer) (Object) this, stat, amount);
    }
}
