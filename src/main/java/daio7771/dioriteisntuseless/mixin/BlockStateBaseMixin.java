package daio7771.dioriteisntuseless.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import daio7771.dioriteisntuseless.block.DioriteStats;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dureza de la diorita (diorite.hardness; vanilla 1.5). getDestroySpeed es el único sitio donde
 * el juego lee la dureza de un bloque colocado (minado, pistones, etc.), así que basta con cambiar
 * lo que devuelve para minecraft:diorite. Las variantes (pulida, losas, escaleras, muros) tienen
 * su propio estado y no se ven afectadas.
 *
 * Se llama muchísimo: primero se descarta cualquier bloque que no sea diorita y luego solo se lee
 * el valor en memoria de DioriteStats, que puede cambiar en caliente.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Shadow
    public abstract Block getBlock();

    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float dioriteisntuseless$dioriteHardness(float original) {
        return this.getBlock() == Blocks.DIORITE ? DioriteStats.hardness(original) : original;
    }
}
