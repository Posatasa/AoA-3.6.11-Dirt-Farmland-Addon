package com.posatasa.aoaagriculture;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns AoA crops into copper coins at the petal crafting station.
 *
 * <p>The payout is AoA's own mineralization station formula - {@code base + random(base)} coins per
 * item, one item per click, nothing consumed in creative - applied to AoA's crops instead of its
 * ores. That formula is why the table stores a single number per crop: a base of 5 pays 5 to 9, 7
 * pays 7 to 13, 11 pays 11 to 21.
 *
 * <p>As with everything else here, no AoA class is involved: the crops and the coin are resolved by
 * registry name, so a rename costs the payout rather than crashing.
 */
public final class CropCoinRitual {
    private static final String COPPER_COIN = "aoa3:copper_coin";

    /** AoA crop -> payout base, where the payout is {@code base + random(base)} coins. */
    private static final String[][] CROPS = {
            {"aoa3:tea_shreddings", "2"},
            {"aoa3:heart_fruit", "3"},
            {"aoa3:bubble_berries", "7"},
            {"aoa3:chilli", "5"},
            {"aoa3:floracle_sticks", "5"},
            {"aoa3:goldicap_petals", "5"},
            {"aoa3:luna_globe", "7"},
            {"aoa3:lunacrike", "7"},
            {"aoa3:lunalons", "7"},
            {"aoa3:rosidons", "5"},
            {"aoa3:thorny_petals", "3"},
            {"aoa3:trilliad_leaves", "11"},
            {"aoa3:eye_bulb", "5"},
            {"aoa3:mystic_shrooms", "5"},
            {"aoa3:magic_marang", "7"},
    };

    private static boolean resolved;
    private static Item coin;
    private static final Map<Item, Integer> payouts = new HashMap<>();

    /** Runs the payout if this click is one, and reports whether it was. */
    public static boolean tryApply(World world, BlockPos pos, PlayerEntity player, ItemStack held) {
        if (!resolve()) {
            return false;
        }
        Integer base = payouts.get(held.getItem());
        if (base == null) {
            return false;
        }
        if (!world.isClientSide) {
            if (!player.isCreative()) {
                held.shrink(1);
            }
            ItemStack reward = new ItemStack(coin, base + player.getRandom().nextInt(base));
            if (!player.inventory.add(reward)) {
                player.drop(reward, false);
            }
            StationFx.play(world, pos, 1.0F);
        }
        return true;
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return coin != null && !payouts.isEmpty();
        }
        resolved = true;
        coin = ForgeRegistries.ITEMS.getValue(new ResourceLocation(COPPER_COIN));
        for (String[] entry : CROPS) {
            Item crop = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry[0]));
            if (crop != null) {
                payouts.put(crop, Integer.valueOf(entry[1]));
            }
        }
        return coin != null && !payouts.isEmpty();
    }

    private CropCoinRitual() {}
}
