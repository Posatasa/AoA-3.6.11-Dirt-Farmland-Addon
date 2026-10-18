package com.posatasa.aoaagriculture.mixin;

import com.posatasa.aoaagriculture.FarmlandBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MushroomBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Gives two AoA soils Farmer's Delight's rich soil behaviour for mushrooms.
 *
 * <p>Farmer's Delight turns a brown or red mushroom standing on rich soil into a mushroom colony
 * when it is dark enough. That lives on the soil block's own random tick, which AoA's soils do not
 * have, so the same rule is applied from the mushroom's tick instead: mushrooms already tick to
 * spread, which makes them the natural place to hang it.
 *
 * <p>Both the colony blocks and the two soils are resolved by registry name, so this does nothing
 * at all when Farmer's Delight is not installed.
 */
@Mixin(MushroomBlock.class)
public abstract class MixinMushroomColony {
    /** Farmer's Delight forms colonies at this light level or darker. */
    private static final int COLONY_LIGHT = 12;

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void aoa3agriculture$formColonyOnAoaSoil(BlockState state, ServerWorld world,
                                                     BlockPos pos, Random random, CallbackInfo ci) {
        if (world.getRawBrightness(pos, 0) > COLONY_LIGHT) {
            return;
        }
        Block below = world.getBlockState(pos.below()).getBlock();
        Block colony = FarmlandBlocks.colonyFor(state.getBlock(), below);
        if (colony == null) {
            return;
        }
        world.setBlockAndUpdate(pos, colony.defaultBlockState());
        ci.cancel();
    }
}
