package daio7771.dioriteisntuseless.mixin.client;

import daio7771.dioriteisntuseless.client.warning.WarningGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * La primera vez que se va a abrir el menú principal, se abre en su lugar la pantalla de aviso
 * (que lleva después al menú). Cambiar la pantalla antes de abrirla evita que el menú se vea
 * un instante antes del aviso.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen dioriteisntuseless$warningFirst(Screen screen) {
        return WarningGate.intercept(screen);
    }
}
