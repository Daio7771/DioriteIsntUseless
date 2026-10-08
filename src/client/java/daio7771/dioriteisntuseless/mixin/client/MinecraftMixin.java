package daio7771.dioriteisntuseless.mixin.client;

import daio7771.dioriteisntuseless.client.abuse.CreditsGate;
import daio7771.dioriteisntuseless.client.warning.WarningGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Pantallas que ocupan el lugar de otra justo antes de abrirse, para que la otra no se vea ni un
 * instante:
 * - La primera vez que se va a abrir el menú principal, la pantalla de aviso (que lleva después
 *   al menú).
 * - Cuando tocan los créditos del Abuse Mode, al abrir la pausa o acostarse (ver CreditsGate).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen dioriteisntuseless$replaceScreen(Screen screen) {
        return CreditsGate.intercept(WarningGate.intercept(screen));
    }
}
