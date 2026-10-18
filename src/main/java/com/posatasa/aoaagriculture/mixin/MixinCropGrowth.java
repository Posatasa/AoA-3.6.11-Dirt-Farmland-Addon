package com.posatasa.aoaagriculture.mixin;

import com.posatasa.aoaagriculture.FarmlandBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropsBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Doubles the growth rate of AoA's own crops on their own dimension's farmland.
 *
 * <p>Vanilla crops grow by rolling {@code nextInt((int) (25 / speed) + 1) == 0} once per random
 * tick, so multiplying the speed is the idiomatic way to make a crop faster rather than a shortcut.
 * AoA's crops - including its two-block ones - all grow through
 * {@link CropsBlock#getGrowthSpeed}, so a single hook covers every one of them.
 *
 * <p>Which crop belongs to which soil is a table in {@code FarmlandBlocks}, matched by registry
 * name, and it is the soil of the farmland (plain or fertilised) that decides, not the block.
 */
@Mixin(CropsBlock.class)
public abstract class MixinCropGrowth {
    @Inject(method = "getGrowthSpeed", at = @At("RETURN"), cancellable = true)
    private static void aoa3agriculture$doubleOnOwnSoil(Block crop, IBlockReader world, BlockPos pos,
                                                        CallbackInfoReturnable<Float> cir) {
        Block below = world.getBlockState(pos.below()).getBlock();
        if (FarmlandBlocks.speedsUpCrop(crop, below)) {
            cir.setReturnValue(cir.getReturnValueF() * 2.0F);
        }
    }
}
