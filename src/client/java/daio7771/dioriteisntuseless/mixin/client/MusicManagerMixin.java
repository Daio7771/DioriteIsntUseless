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
 * While the Abuse Mode background is playing, Minecraft's music stays silent: whatever is playing
 * is cut and no new track starts (decided by Daio: the background rules). When the background
 * ends, the music comes back on its own, with the normal wait between tracks.
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
