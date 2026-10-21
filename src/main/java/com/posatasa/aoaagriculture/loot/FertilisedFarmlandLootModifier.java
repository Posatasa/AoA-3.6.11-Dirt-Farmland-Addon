package com.posatasa.aoaagriculture.loot;

import com.google.gson.JsonObject;
import com.posatasa.aoaagriculture.block.AoaFertilisedFarmlandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropsBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.loot.conditions.ILootCondition;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.loot.GlobalLootModifierSerializer;
import net.minecraftforge.common.loot.LootModifier;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.ListIterator;

/**
 * AoA's harvest bonus, applied to this add-on's fertilised farmlands.
 *
 * <p>AoA implements this for {@code aoa3:fertilised_farmland} with its own global loot modifier,
 * which recognises that block by class. Our per-soil blocks are not that class, so they need this
 * equivalent - otherwise harvesting a crop on them would give no bonus and, worse, would leave the
 * block fully fertilised forever instead of stepping down.
 *
 * <p>The rules are AoA's, per its documentation: a mature crop harvested over a well fertilised
 * block drops two extra of every non-seed item and leaves a plain fertilised block behind; over a
 * plain fertilised block it drops one extra and leaves the plain farmland behind. Moisture is
 * preserved either way, and AoA's green manure crop is exempt so that it can keep the block well
 * fertilised.
 */
public class FertilisedFarmlandLootModifier extends LootModifier {
    /** AoA's green manure crop, recognised by registry name so no AoA class is referenced. */
    private static final String GREEN_MANURE = "green_manure";

    public FertilisedFarmlandLootModifier(ILootCondition[] conditions) {
        super(conditions);
    }

    @Nonnull
    @Override
    protected List<ItemStack> doApply(List<ItemStack> generatedLoot, LootContext context) {
        if (generatedLoot.isEmpty()) {
            return generatedLoot;
        }
        BlockState cropState = context.getParamOrNull(LootParameters.BLOCK_STATE);
        Vector3d origin = context.getParamOrNull(LootParameters.ORIGIN);
        if (cropState == null || origin == null) {
            return generatedLoot;
        }
        Block crop = cropState.getBlock();
        // CropsBlock only, on purpose. Farmer's Delight's tomato is a BushBlock, and the bonus was
        // extended to any crop-type plant at one point - but its own harvest is not worth changing,
        // so it stays out: a tomato planted here simply behaves as it does on any other farmland.
        if (!(crop instanceof CropsBlock) || isGreenManure(crop)) {
            return generatedLoot;
        }

        BlockPos farmlandPos = new BlockPos(origin).below();
        BlockState below = context.getLevel().getBlockState(farmlandPos);
        Block blockBelow = below.getBlock();
        if (!(blockBelow instanceof AoaFertilisedFarmlandBlock)) {
            return generatedLoot;
        }
        if (!below.canSustainPlant(context.getLevel(), farmlandPos, Direction.UP,
                (IPlantable) crop)) {
            return generatedLoot;
        }

        boolean well = below.getValue(AoaFertilisedFarmlandBlock.WELL_FERTILISED);
        int bonus = well ? 2 : 1;

        // The crop's own seed item is excluded from the bonus, so a farm cannot bootstrap itself.
        ItemStack seeds = crop.getCloneItemStack(context.getLevel(), new BlockPos(origin), cropState);
        ListIterator<ItemStack> iterator = generatedLoot.listIterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (!seeds.isEmpty() && stack.getItem() == seeds.getItem()) {
                continue;
            }
            int room = stack.getMaxStackSize() - stack.getCount();
            if (room >= bonus) {
                stack.grow(bonus);
            } else {
                // Spill the remainder into a separate stack rather than losing it.
                stack.grow(room);
                ItemStack extra = stack.copy();
                extra.setCount(bonus - room);
                iterator.add(extra);
            }
        }

        BlockState replacement = replacementFor(blockBelow, well, below);
        context.getLevel().setBlock(farmlandPos, replacement, 3);
        return generatedLoot;
    }

    /** Well fertilised steps down to plain fertilised; plain fertilised steps down to farmland. */
    private static BlockState replacementFor(Block blockBelow, boolean well, BlockState below) {
        Block target = blockBelow;
        if (!well) {
            Block farmland = ((AoaFertilisedFarmlandBlock) blockBelow).farmland();
            if (farmland != null) {
                target = farmland;
            }
        }
        BlockState state = target.defaultBlockState();
        if (target instanceof net.minecraft.block.FarmlandBlock) {
            state = state.setValue(net.minecraft.block.FarmlandBlock.MOISTURE,
                    below.getValue(net.minecraft.block.FarmlandBlock.MOISTURE));
        }
        return state;
    }

    private static boolean isGreenManure(Block crop) {
        ResourceLocation name = crop.getRegistryName();
        return name != null && GREEN_MANURE.equals(name.getPath())
                && "aoa3".equals(name.getNamespace());
    }

    /** Serializer for {@code data/aoaagriculture/loot_modifiers/*.json}. */
    public static class Serializer
            extends GlobalLootModifierSerializer<FertilisedFarmlandLootModifier> {
        @Override
        public FertilisedFarmlandLootModifier read(ResourceLocation location, JsonObject object,
                                                   ILootCondition[] conditions) {
            return new FertilisedFarmlandLootModifier(conditions);
        }

        @Override
        public JsonObject write(FertilisedFarmlandLootModifier instance) {
            return new JsonObject();
        }
    }
}
