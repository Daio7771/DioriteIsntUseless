package daio7771.dioriteisntuseless.mixin.client;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the client change the maximum durability of the dioritine tools to match the remote
 * server's (see ClientConfigSync). The field is changed once when the packet arrives, so it adds
 * no work to getMaxDamage, which is called a huge number of times.
 */
@Mixin(Item.class)
public interface ItemAccessor {

    @Mutable
    @Accessor("maxDamage")
    void dioriteisntuseless$setMaxDamage(int maxDamage);
}
