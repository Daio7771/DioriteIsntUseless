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
 * Hardness of diorite (diorite.hardness; vanilla 1.5). getDestroySpeed is the only place where the
 * game reads the hardness of a placed block (mining, pistons, etc.), so changing what it returns
 * for minecraft:diorite is enough. The variants (polished, slabs, stairs, walls) have their own
 * state and are not affected.
 *
 * It is called a huge number of times: first any block that is not diorite is ruled out, and then
 * only the in-memory value from DioriteStats is read, which can change while the game runs.
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
