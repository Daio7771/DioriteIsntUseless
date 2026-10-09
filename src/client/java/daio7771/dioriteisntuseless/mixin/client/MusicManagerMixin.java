package daio7771.dioriteisntuseless.mixin.client;

import daio7771.dioriteisntuseless.client.abuse.AbuseAmbience;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mientras suena el fondo del Abuse Mode, la música de Minecraft calla: la que suene se corta y no
 * empieza otra (decidido por Daio: el fondo manda). Cuando el fondo acaba, la música vuelve sola,
 * con la espera normal entre canciones.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Shadow
    @Nullable
    private SoundInstance currentMusic;

    @Shadow
    public abstract void stopPlaying();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dioriteisntuseless$silenceForAmbience(CallbackInfo ci) {
        if (!AbuseAmbience.blocksMusic()) return;
        if (this.currentMusic != null) this.stopPlaying();
        ci.cancel();
    }
}
