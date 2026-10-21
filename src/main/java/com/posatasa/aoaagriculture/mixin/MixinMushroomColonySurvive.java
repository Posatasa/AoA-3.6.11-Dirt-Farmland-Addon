package com.posatasa.aoaagriculture.mixin;

import com.posatasa.aoaagriculture.registry.FarmlandBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Farmer's Delight's mushroom colonies stay alive on the two AoA soils that act like rich soil.
 *
 * <p>A colony survives on Farmer's Delight's own rich soil because that block is in vanilla's
 * {@code minecraft:mushroom_grow_block} tag. The obvious shortcut would be to put the AoA soils in
 * that tag too - but vanilla reads it to decide where giant mushrooms may grow, so they would then
 * produce giant mushrooms even with Farmer's Delight absent. This hook reaches the same result
 * without touching any vanilla behaviour: it targets Farmer's Delight's own class by name, so it
 * exists only while that mod is installed, and it does nothing unless the block below is one of the
 * two soils Farmer's Delight behaviour was extended to.
 */
@Mixin(targets = "vectorwing.farmersdelight.blocks.MushroomColonyBlock")
public abstract class MixinMushroomColonySurvive {
    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true, require = 0)
    private void aoa3agriculture$surviveOnAoaSoil(BlockState state, IWorldReader world, BlockPos pos,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        if (FarmlandBlocks.isColonySoil(world.getBlockState(pos.below()).getBlock())) {
            cir.setReturnValue(true);
        }
    }
}
