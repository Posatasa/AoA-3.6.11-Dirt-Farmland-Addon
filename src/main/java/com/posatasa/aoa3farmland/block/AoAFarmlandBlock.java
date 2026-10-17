package com.posatasa.aoa3farmland.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;

import javax.annotation.Nullable;

/**
 * Vanilla farmland with two differences, both about the soil underneath it:
 *
 * <ul>
 *   <li>when an entity lands on it hard enough to break it, it reverts to the AoA soil it was
 *       tilled from instead of to {@code minecraft:dirt};
 *   <li>it supports crops through Forge's {@link IPlantable} contract, which vanilla farmland only
 *       does by being {@code minecraft:farmland} itself.
 * </ul>
 *
 * <p>Everything else - the moisture property, the drying rules, the 15/16 shape, and not being
 * tillable itself - is inherited from {@link FarmlandBlock}, so these blocks are farmland in every
 * way the game (and other mods) can observe.
 *
 * <p>Vanilla's own crops ({@code CropsBlock}, {@code StemBlock}) do not ask that contract: they
 * hard-code "the block below is minecraft:farmland". {@code com.posatasa.aoa3farmland.mixin}
 * widens that check for these blocks; {@link #canSustainPlant} covers every Forge-aware crop.
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

    /** Lets Forge-aware crops grow here, the way they grow on vanilla farmland. */
    @Override
    public boolean canSustainPlant(BlockState state, IBlockReader world, BlockPos pos,
                                   Direction facing, IPlantable plantable) {
        return plantable.getPlantType(world, pos.relative(facing)) == PlantType.CROP;
    }

    /** Vanilla farmland counts as fertile while it is wet; crops grow faster on it. */
    @Override
    public boolean isFertile(BlockState state, IBlockReader world, BlockPos pos) {
        return state.getValue(MOISTURE) > 0;
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
