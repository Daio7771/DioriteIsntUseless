package daio7771.dioriteisntuseless.mixin.client;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Permite cambiar la durabilidad máxima del Dioritine Axe en el cliente para que coincida con la
 * del servidor remoto (ver ClientConfigSync). Se cambia el campo una vez al recibir el paquete,
 * así que no añade trabajo a getMaxDamage, que se llama muchísimo.
 */
@Mixin(Item.class)
public interface ItemAccessor {

    @Mutable
    @Accessor("maxDamage")
    void dioriteisntuseless$setMaxDamage(int maxDamage);
}
