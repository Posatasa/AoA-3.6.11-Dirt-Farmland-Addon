package com.posatasa.aoa3farmland.mixin;

import com.posatasa.aoa3farmland.block.AoAFarmlandBlock;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropsBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets vanilla crops be planted on this add-on's farmland.
 *
 * <p>{@code CropsBlock} and the two stem blocks decide where they may be planted with
 *
 * <pre>protected boolean mayPlaceOn(BlockState state, ...) { return state.is(Blocks.FARMLAND); }</pre>
 *
 * <p>That is an identity comparison against the block {@code minecraft:farmland}, so no modded
 * farmland can ever satisfy it - not this add-on's, and not AoA's own {@code fertilised_farmland}
 * either. Widening the answer to "vanilla farmland, or one of our farmlands" is the only way to
 * make vanilla and AoA crops plantable here, and it changes nothing for any other block.
 *
 * <p>The mixin only ever widens a {@code false} result, so every other placement rule (light
 * level, an air block above, and so on) stays exactly vanilla's.
 */
@Mixin({ CropsBlock.class, StemBlock.class, AttachedStemBlock.class })
public abstract class MixinMayPlaceOn {
    @Inject(method = "mayPlaceOn", at = @At("RETURN"), cancellable = true)
    private void aoa3farmland$allowOurFarmland(BlockState state, IBlockReader world, BlockPos pos,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && state.getBlock() instanceof AoAFarmlandBlock) {
            cir.setReturnValue(true);
        }
    }
}
