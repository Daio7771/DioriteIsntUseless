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
 * Resistencia a explosiones de la diorita (diorite.blastResistance; vanilla 6).
 *
 * No se cambia Block.getExplosionResistance() de Blocks.DIORITE porque StairBlock devuelve la
 * resistencia de su bloque base, y las escaleras de diorita también subirían a 12. En su lugar
 * se cambia el valor donde el cálculo de explosiones lo lee, mirando el bloque que hay de verdad
 * en esa posición. Todas las explosiones vanilla (creeper, TNT, camas, cristales, wither...)
 * pasan por aquí.
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
