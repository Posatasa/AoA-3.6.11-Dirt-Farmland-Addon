package com.posatasa.aoaagriculture.mixin;

import com.posatasa.aoaagriculture.block.AoAFarmlandBlock;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BushBlock;
import net.minecraft.block.CropsBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets crops be planted on this add-on's farmland.
 *
 * <p>{@code CropsBlock} and the two stem blocks decide where they may be planted with
 *
 * <pre>protected boolean mayPlaceOn(BlockState state, ...) { return state.is(Blocks.FARMLAND); }</pre>
 *
 * <p>an identity comparison against the block {@code minecraft:farmland}, so no modded farmland can
 * ever satisfy it - not this add-on's, and not AoA's own {@code fertilised_farmland} either.
 * Widening the answer to "vanilla farmland, or one of our farmlands" is what makes vanilla and AoA
 * crops plantable here, and it changes nothing for any other block.
 *
 * <p>{@code BushBlock} is handled in the same injection for a different reason: it gates on a
 * hardcoded list of vanilla soil blocks rather than a tag, which is why Farmer's Delight's tomato -
 * a {@code BushBlock} that reports {@link PlantType#CROP} - refuses ordinary modded farmland. There
 * the widening is limited to plants that actually belong on farmland, so flowers and saplings stay
 * exactly as restricted as vanilla keeps them.
 *
 * <p>Every branch only ever widens a {@code false} result, so all the other placement rules (light
 * level, an air block above, and so on) stay exactly vanilla's.
 */
@Mixin({ CropsBlock.class, StemBlock.class, AttachedStemBlock.class, BushBlock.class })
public abstract class MixinMayPlaceOn {
    @Inject(method = "mayPlaceOn", at = @At("RETURN"), cancellable = true)
    private void aoaagriculture$allowOurFarmland(BlockState state, IBlockReader world, BlockPos pos,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !(state.getBlock() instanceof AoAFarmlandBlock)) {
            return;
        }
        Block plant = (Block) (Object) this;
        if (!(plant instanceof BushBlock) || plant instanceof CropsBlock || plant instanceof StemBlock
                || plant instanceof AttachedStemBlock) {
            cir.setReturnValue(true);                  // the three blocks this always covered
            return;
        }
        // A BushBlock that is a crop (Farmer's Delight's tomato) belongs on farmland; a flower or
        // a sapling does not, so it keeps vanilla's answer.
        if (plant instanceof IPlantable
                && ((IPlantable) plant).getPlantType(world, pos.above()) == PlantType.CROP) {
            cir.setReturnValue(true);
        }
    }
}
