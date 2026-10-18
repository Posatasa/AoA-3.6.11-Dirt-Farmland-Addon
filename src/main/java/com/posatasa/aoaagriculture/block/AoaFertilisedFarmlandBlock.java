package com.posatasa.aoaagriculture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;

import javax.annotation.Nullable;

/**
 * The fertilised counterpart of one of this add-on's farmlands - the per-soil version of AoA's
 * {@code aoa3:fertilised_farmland}.
 *
 * <p>It is a farmland first: everything {@link AoAFarmlandBlock} does (moisture, crops, reverting to
 * its own AoA soil when dried out, covered or trampled) still applies. On top of that AoA's block
 * adds one state, {@code well_fertilised}, which is what the harvest bonus is keyed on: a well
 * fertilised block gives two extra drops and drops to a normal fertilised one, and a normal
 * fertilised block gives one extra drop and drops to the plain farmland it came from.
 *
 * <p>{@code well_fertilised} is set by AoA's green manure crop and cleared by harvesting, matching
 * AoA's own rules for its single fertilised farmland block.
 */
public class AoaFertilisedFarmlandBlock extends AoAFarmlandBlock {
    /** AoA's own property name for "this block has been fully fertilised". */
    public static final BooleanProperty WELL_FERTILISED = BooleanProperty.create("well_fertilised");

    /** The plain farmland of the same soil, i.e. what harvesting downgrades this block into. */
    @Nullable
    private Block farmland;

    public AoaFertilisedFarmlandBlock(Properties properties, @Nullable Block soil) {
        super(properties, soil);
        registerDefaultState(defaultBlockState().setValue(WELL_FERTILISED, false));
    }

    /** Called once all mods have registered, with the plain farmland of the same soil. */
    public void setFarmland(@Nullable Block farmland) {
        this.farmland = farmland;
    }

    /** The plain farmland of the same soil, or null before the game has finished loading. */
    @Nullable
    public Block farmland() {
        return farmland;
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WELL_FERTILISED);
    }
}
