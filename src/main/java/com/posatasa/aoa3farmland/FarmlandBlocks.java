package com.posatasa.aoa3farmland;

import com.posatasa.aoa3farmland.block.AoAFarmlandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration and lookup for the farmland blocks.
 *
 * <p>One farmland is added for every AoA block that AoA itself puts in the {@code forge:dirt}
 * block tag, except for polluted soil (and therefore polluted grass), which is deliberately left
 * alone. That tag is this mod's definition of "AoA's dirt blocks": it is the same set AoA treats
 * as dirt for every other purpose, and it excludes the stone-like block that merely happens to be
 * named dirt (archaic dirt is a pickaxe-mined stone block, not soil) as well as the handful of AoA
 * grass variants that sit on stone or rock rather than on soil.
 *
 * <p>Nothing here names an AoA class. Every association to AoA's blocks is made by registry name
 * after all mods have finished registering, so this add-on contains no AoA code and needs no
 * AoA classes on its compile classpath.
 */
public final class FarmlandBlocks {
    public static final String MOD_ID = "aoa3farmland";

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    /** One entry per AoA soil: the farmland we add for it, and the names it comes from. */
    private static final List<Soil> SOILS = new ArrayList<>();

    /** Our own block instances, by registry name. Filled in by the registry event. */
    private static final Map<String, Block> OURS = new HashMap<>();

    /**
     * Source block (an AoA soil or the grass growing on it) -> the AoA farmland a hoe turns it
     * into. Filled in at load-complete; see {@link #onLoadComplete}.
     */
    private static final Map<Block, Block> BY_SOURCE = new HashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(FarmlandBlocks::onRegisterBlocks);
        bus.addListener(FarmlandBlocks::onLoadComplete);
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    /**
     * The farmland a hoe should produce when used on {@code source}, or null when that block is
     * not one of AoA's soils. Consulted from {@link AoAFarmlandBlock#getToolModifiedState}.
     */
    public static Block farmlandFor(Block source) {
        return BY_SOURCE.get(source);
    }

    /**
     * Adds one farmland. {@code soil} is what it drops and what it reverts to when trampled; the
     * soil itself and the grass that grows on it are the blocks a hoe can turn into it.
     */
    private static void add(String name, String soil, String grass) {
        RegistryObject<Block> block = BLOCKS.register(name,
                () -> new AoAFarmlandBlock(properties(), null));
        ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().tab(ItemGroup.TAB_DECORATIONS)));
        SOILS.add(new Soil(soil, grass, MOD_ID + ":" + name));
    }

    private static Block.Properties properties() {
        // Vanilla farmland's own properties: hardness and explosion resistance through one call,
        // the gravel sound, and the two collision predicates forced true because the block is only
        // 15/16 of a block tall. The shovel tool type matches vanilla farmland and the AoA soils.
        return Block.Properties.of(Material.DIRT)
                .strength(0.6F)
                .sound(SoundType.GRAVEL)
                .isSuffocating((state, world, pos) -> true)
                .isViewBlocking((state, world, pos) -> true)
                .harvestTool(ToolType.SHOVEL);
    }

    /** Records our own block instances, which the registry event can now resolve by name. */
    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        IForgeRegistry<Block> registry = event.getRegistry();
        for (Soil soil : SOILS) {
            Block block = registry.getValue(new ResourceLocation(soil.farmland));
            if (block != null) {
                OURS.put(soil.farmland, block);
            }
        }
    }

    /**
     * Binds each AoA soil (and its grass) to the farmland we added for it, and teaches the
     * farmland which soil to fall back to when trampled.
     *
     * <p>This has to happen after <em>all</em> mods have finished registering, not inside the
     * block registry event: that event fires once per mod in an unspecified order, so looking
     * AoA's blocks up while it is still running would find them missing whenever this mod happens
     * to come first. Load-complete is past every registration, so these lookups cannot miss.
     */
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        IForgeRegistry<Block> registry = ForgeRegistries.BLOCKS;
        for (Soil soil : SOILS) {
            Block farmland = OURS.get(soil.farmland);
            if (!(farmland instanceof AoAFarmlandBlock)) {
                continue;
            }
            Block soilBlock = registry.getValue(new ResourceLocation(soil.soil));
            ((AoAFarmlandBlock) farmland).setSoil(soilBlock);
            for (String source : new String[] { soil.soil, soil.grass }) {
                Block from = registry.getValue(new ResourceLocation(source));
                if (from != null) {
                    BY_SOURCE.put(from, farmland);
                }
            }
        }
    }

    // ---- the soils -------------------------------------------------------------------------
    //                         farmland name             AoA soil                its grass
    static {
        add("aromatic_farmland",   "aoa3:aromatic_dirt",   "aoa3:aromatic_grass");
        add("blackened_farmland",  "aoa3:blackened_soil",  "aoa3:faded_grass");
        add("candied_farmland",    "aoa3:candied_dirt",    "aoa3:candied_grass");
        add("celevian_farmland",   "aoa3:celevian_dirt",   "aoa3:celevian_grass");
        add("coral_farmland",      "aoa3:coral_soil",      "aoa3:coral_grass");
        add("creep_farmland",      "aoa3:creep_dirt",      "aoa3:creep_grass");
        add("fungal_farmland",     "aoa3:fungal_dirt",     "aoa3:fungal_grass");
        add("greckon_farmland",    "aoa3:greckon_dirt",    "aoa3:greckon_grass");
        add("lunalyte_farmland",   "aoa3:lunalyte_dirt",   "aoa3:lunalyte_grass");
        add("lunasole_farmland",   "aoa3:lunasole_dirt",   "aoa3:lunasole_grass");
        add("weightless_farmland", "aoa3:weightless_dirt", "aoa3:weightless_grass");
    }

    private FarmlandBlocks() {}

    /** The three registry names that make up one soil's farmland. */
    private static final class Soil {
        private final String soil;
        private final String grass;
        private final String farmland;

        private Soil(String soil, String grass, String farmland) {
            this.soil = soil;
            this.grass = grass;
            this.farmland = farmland;
        }
    }
}
