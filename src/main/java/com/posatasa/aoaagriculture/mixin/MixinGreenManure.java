package com.posatasa.aoaagriculture.mixin;

import com.posatasa.aoaagriculture.FarmlandBlocks;
import com.posatasa.aoaagriculture.block.AoaFertilisedFarmlandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropsBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes AoA's green manure crop well-fertilise this add-on's farmlands.
 *
 * <p>Breaking a mature green manure crop is the only way AoA's {@code well_fertilised} state is ever
 * set: the crop replaces the block beneath it with an AoA fertilised farmland carrying that flag.
 * Its check is {@code instanceof FarmlandBlock}, which our blocks pass - but the block it writes is
 * AoA's single fertilised farmland, so our soil would silently drop out of the per-soil chain.
 * This takes over only in that case and writes the matching per-soil block instead, keeping AoA's
 * own rule (mature crop, moisture preserved, {@code well_fertilised} true).
 */
@Mixin(targets = "net.tslat.aoa3.content.block.functional.misc.GreenManure")
public abstract class MixinGreenManure {
    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true, require = 0)
    private void aoaagriculture$wellFertiliseOurFarmland(IWorld world, BlockPos pos, BlockState state,
                                                        CallbackInfo ci) {
        // The target is a crop block; the (Object) hop is the usual mixin cast, since this class
        // is merged into the target rather than extending it.
        CropsBlock crop = (CropsBlock) (Object) this;
        // getAge is protected, so read the age through the public property instead.
        if (state.getValue(crop.getAgeProperty()) < crop.getMaxAge()) {
            return;                                   // AoA only does this for a mature crop
        }
        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        Block fertilised = FarmlandBlocks.fertilisedFor(below.getBlock());
        if (fertilised == null) {
            return;                                   // not ours: let AoA do exactly what it does
        }
        BlockState replacement = fertilised.defaultBlockState()
                .setValue(AoaFertilisedFarmlandBlock.WELL_FERTILISED, true)
                .setValue(FarmlandBlock.MOISTURE,
                        below.getValue(FarmlandBlock.MOISTURE));
        world.setBlock(belowPos, replacement, 3);
        ci.cancel();
    }
}
