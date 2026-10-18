package com.posatasa.aoaagriculture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.FarmlandWaterManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Vanilla farmland that reverts to the AoA soil it was tilled from instead of to
 * {@code minecraft:dirt}, in all three places vanilla farmland can be destroyed.
 *
 * <p>That last part is why this class overrides {@link #tick} and {@link #randomTick} as well as
 * {@link #fallOn}: vanilla turns farmland into dirt through a <em>static</em> helper,
 * {@code FarmlandBlock.turnToDirt}, which names {@code Blocks.DIRT} directly. Being static there is
 * no virtual dispatch to hook, so a trampling-only override (the obvious reading of the class) left
 * the other two paths - drying out without water, and having a block placed on top - turning these
 * blocks into plain vanilla dirt. The two overrides below are vanilla's own logic with that one
 * block swapped.
 *
 * <p>Everything else is inherited from {@link FarmlandBlock}: the moisture property and its range,
 * the 15/16 shape, the "must not be covered" rule, and not being tillable itself.
 */
public class AoAFarmlandBlock extends FarmlandBlock {
    /** Vanilla's top moisture level; the property allows 0..7. */
    private static final int MAX_MOISTURE = 7;

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

    /** Vanilla: a scheduled tick turns farmland into dirt when something is placed on top. */
    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!state.canSurvive(world, pos)) {
            turnToSoil(state, world, pos);
        }
    }

    /** Vanilla's drying and wetting, with {@link #turnToSoil} where vanilla calls turnToDirt. */
    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int moisture = state.getValue(MOISTURE);
        if (!isNearWater(world, pos) && !world.isRainingAt(pos.above())) {
            if (moisture > 0) {
                world.setBlock(pos, state.setValue(MOISTURE, moisture - 1), 2);
            } else if (!isUnderCrops(world, pos)) {
                turnToSoil(state, world, pos);
            }
        } else if (moisture < MAX_MOISTURE) {
            world.setBlock(pos, state.setValue(MOISTURE, MAX_MOISTURE), 2);
        }
    }

    /** Vanilla: trampling farmland turns it into dirt. */
    @Override
    public void fallOn(World world, BlockPos pos, Entity entity, float fallDistance) {
        BlockState state = world.getBlockState(pos);
        if (!world.isClientSide
                && state.getBlock() == this
                && ForgeHooks.onFarmlandTrample(world, pos, state, fallDistance, entity)) {
            turnToSoil(state, world, pos);
        }
        super.fallOn(world, pos, entity, fallDistance);
    }

    /**
     * The one place this block differs from vanilla: it becomes its own soil, not {@code dirt}.
     *
     * <p>{@link Block#pushEntitiesUp} is vanilla's own helper, and it matters: soil is a full block
     * where farmland is a pixel short, so anything standing on it has to be nudged up.
     */
    private void turnToSoil(BlockState state, World world, BlockPos pos) {
        Block target = soil == null ? Blocks.DIRT : soil;
        world.setBlockAndUpdate(pos,
                Block.pushEntitiesUp(state, target.defaultBlockState(), world, pos));
    }

    /** Vanilla's {@code FarmlandBlock#isNearWater}, which is private there. */
    private static boolean isNearWater(IWorldReader world, BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            FluidState fluid = world.getFluidState(check);
            if (fluid.is(FluidTags.WATER)) {
                return true;
            }
        }
        return FarmlandWaterManager.hasBlockWaterTicket(world, pos);
    }

    /** Vanilla's {@code FarmlandBlock#isUnderCrops}, which is private there. */
    private boolean isUnderCrops(IBlockReader world, BlockPos pos) {
        BlockState plant = world.getBlockState(pos.above());
        BlockState state = world.getBlockState(pos);
        return plant.getBlock() instanceof IPlantable
                && state.canSustainPlant(world, pos, Direction.UP, (IPlantable) plant.getBlock());
    }
}
