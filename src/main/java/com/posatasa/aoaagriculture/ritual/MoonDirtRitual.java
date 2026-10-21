package com.posatasa.aoaagriculture.ritual;

import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;


/**
 * The moon soil ritual at AoA's petal crafting station.
 *
 * <p>Right-clicking the station with dye turns vanilla dirt in the player's inventory into that
 * moon's soil: pink dye makes Lunalyte (the pink moon), blue dye makes Lunasole (the blue moon).
 *
 * <p>One ritual converts up to a full stack, and costs a dye and a rosid root per dirt block plus a
 * single lunar ingot for the whole batch - the ingot is the catalyst, so it is spent once no matter
 * how small the batch is. Whatever the player has least of decides the size, so nothing is consumed
 * that cannot be converted.
 *
 * <p>Every piece of AoA here is looked up by registry name, with the same reasoning as the rest of
 * this mod: no AoA class is referenced, so a renamed item degrades to "the ritual does nothing"
 * rather than a crash. The block itself is reached by a mixin on the station, configured by name.
 */
public final class MoonDirtRitual {
    /** The most dirt one ritual converts, matching a full stack of dye and of roots. */
    private static final int MAX_BATCH = 64;

    private static final String LUNAR_INGOT = "aoa3:lunar_ingot";
    private static final String ROSID_ROOT = "aoa3:rosid_root";
    private static final String PINK_MOON_SOIL = "aoa3:lunalyte_dirt";
    private static final String BLUE_MOON_SOIL = "aoa3:lunasole_dirt";

    private static boolean resolved;
    private static Item lunarIngot;
    private static Item rosidRoot;
    private static Item pinkMoonSoil;
    private static Item blueMoonSoil;

    /**
     * Runs the ritual if this interaction is one, and reports whether it was.
     *
     * @param held the stack in the hand that clicked the station; the ritual uses it up first
     * @return true when the ritual handled the click, so the station's own behaviour is skipped
     */
    public static boolean tryApply(World world, BlockPos pos, PlayerEntity player, ItemStack held) {
        if (!resolve()) {
            return false;
        }
        Item moonSoil;
        if (held.getItem() == Items.PINK_DYE) {
            moonSoil = pinkMoonSoil;
        } else if (held.getItem() == Items.BLUE_DYE) {
            moonSoil = blueMoonSoil;
        } else {
            return false;
        }
        if (moonSoil == null) {
            return false;
        }

        // The smallest of the four numbers decides the batch: a dye and a root are spent per block,
        // and the ingot is a one-off catalyst - so if any of them is missing, nothing is taken.
        int batch = Math.min(MAX_BATCH,
                Math.min(held.getCount(), Math.min(count(player, rosidRoot), count(player, Items.DIRT))));
        if (batch <= 0 || count(player, lunarIngot) < 1) {
            return false;
        }

        if (!world.isClientSide) {
            if (!player.isCreative()) {
                held.shrink(batch);
                consume(player, rosidRoot, batch);
                consume(player, lunarIngot, 1);
            }
            convert(player, batch, moonSoil);
            StationFx.play(world, pos, 0.8F);
        }
        return true;
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return lunarIngot != null && rosidRoot != null && pinkMoonSoil != null
                    && blueMoonSoil != null;
        }
        resolved = true;
        lunarIngot = item(LUNAR_INGOT);
        rosidRoot = item(ROSID_ROOT);
        pinkMoonSoil = item(PINK_MOON_SOIL);
        blueMoonSoil = item(BLUE_MOON_SOIL);
        return lunarIngot != null && rosidRoot != null && pinkMoonSoil != null
                && blueMoonSoil != null;
    }

    private static Item item(String registryName) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(registryName));
    }

    private static int count(PlayerEntity player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
            ItemStack stack = player.inventory.getItem(slot);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consume(PlayerEntity player, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.inventory.getItem(slot);
            if (stack.getItem() == item) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
        player.inventory.setChanged();
    }

    /** Replaces dirt in place, so a converted stack stays in the slot the player expects. */
    private static void convert(PlayerEntity player, int amount, Item moonSoil) {
        int remaining = amount;
        for (int slot = 0; slot < player.inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.inventory.getItem(slot);
            if (stack.getItem() != Items.DIRT) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            if (taken == stack.getCount()) {
                player.inventory.setItem(slot, new ItemStack(moonSoil, taken));
            } else {
                stack.shrink(taken);
                ItemStack converted = new ItemStack(moonSoil, taken);
                if (!player.inventory.add(converted)) {
                    player.drop(converted, false);
                }
            }
            remaining -= taken;
        }
        player.inventory.setChanged();
    }

    private MoonDirtRitual() {}
}
