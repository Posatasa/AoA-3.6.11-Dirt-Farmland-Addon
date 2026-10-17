package com.posatasa.aoa3farmland.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

import javax.annotation.Nullable;

/**
 * Vanilla farmland with one difference: when an entity lands on it hard enough to break it, it
 * reverts to the AoA soil it was tilled from instead of to {@code minecraft:dirt}.
 *
 * <p>Everything else - the moisture property, the drying rules, the crop-support behaviour, the
 * 15/16 shape, and not being tillable itself - is inherited from {@link FarmlandBlock}, so these
 * blocks are farmland in every way the game (and other mods) can observe.
 *
 * <p>Note that tilling the AoA soil is *not* handled here: Forge calls {@code getToolModifiedState}
 * on the block being tilled, which is the AoA block, not this one. See
 * {@code FarmlandBlocks#onUseHoe}.
 */
public class AoAFarmlandBlock extends FarmlandBlock {
    /**
     * The AoA soil this farmland was tilled from, or null before the game has finished loading.
     * Set by {@code FarmlandBlocks}, once every mod has registered.
     */
    @Nullable
    private Block soil;

    public AoAFarmlandBlock(Properties properties, @Nullable Block soil) {
        super(properties);
        this.soil = soil;
    }

    /** Called once all mods have registered, with the block this farmland belongs to. */
    public void setSoil(@Nullable Block soil) {
        this.soil = soil;
    }

    @Override
    public void fallOn(World world, BlockPos pos, Entity entity, float fallDistance) {
        BlockState state = world.getBlockState(pos);
        if (!world.isClientSide
                && state.getBlock() == this
                && ForgeHooks.onFarmlandTrample(world, pos, state, fallDistance, entity)) {
            Block target = soil == null ? Blocks.DIRT : soil;
            world.setBlockAndUpdate(pos, target.defaultBlockState());
        }
        super.fallOn(world, pos, entity, fallDistance);
    }
}
