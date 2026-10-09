package daio7771.dioriteisntuseless.mixin.client;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Time left for the item name that floats above the hotbar when switching items.
 * NonsenseNames does not change any name while it is visible.
 */
@Mixin(Gui.class)
public interface GuiAccessor {

    @Accessor("toolHighlightTimer")
    int dioriteisntuseless$getToolHighlightTimer();
}
