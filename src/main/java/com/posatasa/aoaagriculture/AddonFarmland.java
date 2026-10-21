package com.posatasa.aoaagriculture;

import com.posatasa.aoaagriculture.registry.FarmlandBlocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds a farmland variant for each of Advent of Ascension's dirt-type blocks, so hoes work on AoA
 * soil the same way they work on vanilla dirt.
 *
 * <p>Nothing here is AoA code or an AoA asset: the blocks are vanilla farmland with AoA's own soil
 * as their drop, and their textures are vanilla's farmland pattern recoloured to AoA's palettes.
 */
@Mod(AddonFarmland.MOD_ID)
public class AddonFarmland {
    public static final String MOD_ID = FarmlandBlocks.MOD_ID;

    public AddonFarmland() {
        IEventBus bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        FarmlandBlocks.register(bus);
    }
}
