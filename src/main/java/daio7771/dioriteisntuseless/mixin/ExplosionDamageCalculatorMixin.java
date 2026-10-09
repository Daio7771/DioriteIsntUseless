package daio7771.dioriteisntuseless.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import daio7771.dioriteisntuseless.block.DioriteStats;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Blast resistance of diorite (diorite.blastResistance; vanilla 6).
 *
 * Block.getExplosionResistance() of Blocks.DIORITE is not changed because StairBlock returns the
 * resistance of its base block, and diorite stairs would go up to 12 too. Instead the value is
 * changed where the explosion calculation reads it, looking at the block that is really at that
 * position. Every vanilla explosion (creeper, TNT, beds, end crystals, wither...) goes through
 * here.
 */
@Mixin(ExplosionDamageCalculator.class)
public abstract class ExplosionDamageCalculatorMixin {

    @ModifyExpressionValue(
            method = "getBlockExplosionResistance",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getExplosionResistance()F")
    )
    private float dioriteisntuseless$dioriteResistance(float original, @Local(argsOnly = true) BlockState state) {
        return state.is(Blocks.DIORITE) ? DioriteStats.explosionResistance(original) : original;
    }
}
