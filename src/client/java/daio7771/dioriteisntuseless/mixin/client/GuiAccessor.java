package daio7771.dioriteisntuseless.mixin.client;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Tiempo que le queda al nombre del ítem que flota sobre la barra rápida al cambiar de ítem.
 * NonsenseNames no cambia ningún nombre mientras se ve.
 */
@Mixin(Gui.class)
public interface GuiAccessor {

    @Accessor("toolHighlightTimer")
    int dioriteisntuseless$getToolHighlightTimer();
}
