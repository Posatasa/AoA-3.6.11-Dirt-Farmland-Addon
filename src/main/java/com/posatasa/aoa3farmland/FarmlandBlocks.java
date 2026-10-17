package com.posatasa.aoa3farmland;

import com.posatasa.aoa3farmland.block.AoAFarmlandBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.event.entity.player.UseHoeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 *
 * <p>Tilling is hooked through {@link UseHoeEvent}, not through the block's
 * {@code getToolModifiedState}: that method is called on the block being tilled, which is the AoA
 * block, so an override on these farmland blocks would never run for AoA soil. Forge fires
 * {@code UseHoeEvent} from {@code HoeItem#useOn} before vanilla's own tilling map is consulted,
 * which is exactly the extension point needed.
 */
public final class FarmlandBlocks {
    public static final String MOD_ID = "aoa3farmland";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /** The first block added, used as this mod's creative tab icon. */
    private static RegistryObject<Block> icon;

    /**
     * This mod's own creative tab. Forge patches {@link ItemGroup} so that a plain
     * {@code new ItemGroup(label)} is registered with a spare index, and vanilla builds the
     * translation key as {@code itemGroup.<label>} - which is what the lang files provide.
     */
    public static final ItemGroup TAB = new ItemGroup(MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(icon.get());
        }
    };

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    /** One entry per AoA soil: the farmland we add for it, and the names it comes from. */
    private static final List<Soil> SOILS = new ArrayList<>();

    /**
     * Source block (an AoA soil or the grass growing on it) -> the AoA farmland a hoe turns it
     * into. Filled by {@link #ensureBound()}.
     */
    private static final Map<Block, Block> BY_SOURCE = new HashMap<>();

    private static volatile boolean bound;

    /**
     * Registers the block/item entries and the two event listeners.
     *
     * <p>Both listeners are plain (non-generic) events, which matters: Forge's
     * {@code EventBus#addListener} throws "Cannot register a generic event listener with
     * addListener" for anything extending {@code GenericEvent} - and that exception is thrown
     * while the mod is being constructed, so it fails the whole game at startup rather than at
     * the point of use. {@code RegistryEvent.Register} is one of those generic events, which is
     * why the block lookups below happen at load-complete instead of inside a registry event.
     */
    public static void register(IEventBus modBus) {
        modBus.addListener(FarmlandBlocks::onLoadComplete);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(FarmlandBlocks::onUseHoe);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    /** Adds one farmland. {@code soil} is what it drops and reverts to when trampled. */
    private static void add(String name, String soil, String grass) {
        RegistryObject<Block> block = BLOCKS.register(name,
                () -> new AoAFarmlandBlock(properties(), null));
        ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().tab(TAB)));
        if (icon == null) {
            icon = block;
        }
        SOILS.add(new Soil(soil, grass, MOD_ID + ":" + name, block));
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

    /**
     * Resolves every AoA soil (and its grass) to the farmland added for it.
     *
     * <p>Deliberately not done inside a registry event: those fire once per mod in an unspecified
     * order, so looking AoA's blocks up while registration is still running would find them
     * missing whenever this mod happens to come first.
     */
    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        ensureBound();
    }

    private static void ensureBound() {
        if (bound) {
            return;
        }
        synchronized (FarmlandBlocks.class) {
            if (bound) {
                return;
            }
            int boundSoils = 0;
            for (Soil soil : SOILS) {
                Block farmland = soil.block.get();
                Block soilBlock = block(soil.soil);
                if (soilBlock == null) {
                    // Not a crash: AoA's registry simply does not have this name, which points at
                    // a version mismatch (or AoA not installed). Say so instead of doing nothing.
                    LOGGER.warn("AoA block {} is missing; {} will not be tillable",
                            soil.soil, soil.name);
                    continue;
                }
                if (farmland instanceof AoAFarmlandBlock) {
                    ((AoAFarmlandBlock) farmland).setSoil(soilBlock);
                }
                for (String source : new String[] { soil.soil, soil.grass }) {
                    Block from = block(source);
                    if (from != null) {
                        BY_SOURCE.put(from, farmland);
                    }
                }
                boundSoils++;
            }
            bound = true;
            LOGGER.info("AoA farmland addon active: {} AoA soils and their grass can be tilled "
                    + "({} source blocks)", boundSoils, BY_SOURCE.size());
        }
    }

    private static Block block(String registryName) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(registryName));
    }

    /**
     * Turns one of AoA's soils into the matching farmland, the way vanilla's hoe tills dirt.
     *
     * <p>Setting {@link Event.Result#ALLOW} tells Forge the tilling was handled: it then spends one
     * point of hoe durability itself, exactly as it would for vanilla dirt.
     */
    @SubscribeEvent
    public static void onUseHoe(UseHoeEvent event) {
        if (event.getResult() != Event.Result.DEFAULT) {
            return;                                    // another handler already dealt with it
        }
        ItemUseContext context = event.getContext();
        World world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (context.getClickedFace() == Direction.DOWN) {
            return;
        }
        if (!world.isEmptyBlock(pos.above())) {
            return;                                    // vanilla refuses to till when covered
        }
        ensureBound();
        Block farmland = BY_SOURCE.get(world.getBlockState(pos).getBlock());
        if (farmland == null) {
            return;
        }
        world.playSound(context.getPlayer(), pos, SoundEvents.HOE_TILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (!world.isClientSide) {
            world.setBlock(pos, farmland.defaultBlockState(), 11);
        }
        event.setResult(Event.Result.ALLOW);
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
        private final String name;
        private final RegistryObject<Block> block;

        private Soil(String soil, String grass, String name, RegistryObject<Block> block) {
            this.soil = soil;
            this.grass = grass;
            this.name = name;
            this.block = block;
        }
    }
}
