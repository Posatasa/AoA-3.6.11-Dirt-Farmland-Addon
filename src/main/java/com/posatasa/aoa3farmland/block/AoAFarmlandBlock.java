package com.posatasa.aoa3farmland.block;

import com.posatasa.aoa3farmland.FarmlandBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolType;

import javax.annotation.Nullable;

/**
 * Vanilla farmland with one difference: when an entity lands on it hard enough to break it, it
 * reverts to the AoA soil it was tilled from instead of to {@code minecraft:dirt}.
 *
 * <p>Everything else - the moisture property, the drying rules, the crop-support behaviour, the
 * 15/16 shape, the tilling itself - is inherited from {@link FarmlandBlock}, so these blocks are
 * farmland in every way the game (and other mods) can observe.
 */
public class AoAFarmlandBlock extends FarmlandBlock {
    /**
     * The AoA soil this farmland was tilled from, or null before the game has finished loading.
     * Set by {@link FarmlandBlocks#onLoadComplete}, which runs once every mod has registered - the
     * block's own supplier runs too early to look AoA's blocks up reliably.
     */
    @Nullable
    private Block soil;

    public AoAFarmlandBlock(Properties properties, @Nullable Block soil) {
        super(properties);
        this.soil = soil;
    }

    /** Called once, at load-complete, with the block this farmland belongs to. */
    public void setSoil(@Nullable Block soil) {
        this.soil = soil;
    }

    /**
     * Called by Forge from {@code HoeItem#useOn} for every block a hoe is used on. If that block is
     * one of AoA's soils (or the grass growing on one of them), this is where the hoe finds the
     * farmland to place - AoA's blocks are not in vanilla's hard-coded tilling map, so without this
     * a hoe does nothing on them. Everything else falls through to vanilla's behaviour.
     */
    @Nullable
    @Override
    public BlockState getToolModifiedState(BlockState state, World world, BlockPos pos,
                                           @Nullable PlayerEntity player, ItemStack stack, ToolType toolType) {
        if (toolType == ToolType.HOE) {
            Block farmland = FarmlandBlocks.farmlandFor(state.getBlock());
            if (farmland != null) {
                return farmland.defaultBlockState();
            }
        }
        return super.getToolModifiedState(state, world, pos, player, stack, toolType);
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
