package daio7771.dioriteisntuseless.mixin.client;

import daio7771.dioriteisntuseless.client.abuse.CreditsGate;
import daio7771.dioriteisntuseless.client.warning.WarningGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Screens that take the place of another one right before it opens, so the other one is not seen
 * even for an instant:
 * - The first time the main menu is about to open, the warning screen (which then leads to the
 *   menu).
 * - When the Abuse Mode credits are due, on opening the pause menu or going to bed (see
 *   CreditsGate).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen dioriteisntuseless$replaceScreen(Screen screen) {
        return CreditsGate.intercept(WarningGate.intercept(screen));
    }
}
