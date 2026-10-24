package com.posatasa.aoaagriculture.registry;

import com.posatasa.aoaagriculture.item.AoABucketItem;
import com.posatasa.aoaagriculture.item.InfiniteWaterBucketItem;
import com.posatasa.aoaagriculture.item.LunarWaterBucketItem;
import com.posatasa.aoaagriculture.item.BucketFamily;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * The bucket items: an empty, water, lava and candied water bucket for each of two AoA metals.
 *
 * <p>Each bucket is a plain {@link AoABucketItem}, i.e. vanilla's bucket with the family mapping
 * layered on. The fluid is resolved eagerly rather than through the supplier constructor: this
 * version's {@code BucketItem} keeps the fluid in a field that its emptying logic reads directly, so
 * a supplier-built bucket would silently refuse to pour. Forge registers fluids before items, so
 * AoA's candied water is already there when these suppliers run.
 */
public final class Buckets {
    private static final Logger LOGGER = LogManager.getLogger(FarmlandBlocks.MOD_ID);

    /** AoA's candied water - the fluid the sugar water buckets hold. */
    private static final String CANDIED_WATER = "aoa3:candied_water";

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FarmlandBlocks.MOD_ID);

    public static final BucketFamily LIMONITE = new BucketFamily("limonite");
    public static final BucketFamily ROSITE = new BucketFamily("rosite");
    /** Shyre's water bucket is a portable water source; the rest of the family is ordinary. */
    public static final BucketFamily SHYRE = new BucketFamily("shyre");
    /** Lunar's water bucket makes the player immune to fall damage while held. */
    public static final BucketFamily LUNAR = new BucketFamily("lunar");

    private static Fluid candiedWater;

    public static void register(IEventBus modBus) {
        family(LIMONITE, AoABucketItem::new);
        family(ROSITE, AoABucketItem::new);
        family(SHYRE, InfiniteWaterBucketItem::new);
        family(LUNAR, LunarWaterBucketItem::new);
        ITEMS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(Buckets::onFall);
    }

    /** How one bucket item is built; the Shyre water bucket is the only one that differs. */
    private interface Factory {
        Item create(Fluid content, Item.Properties properties, BucketFamily family);
    }

    private static void family(BucketFamily family, Factory waterFactory) {
        String metal = family.metal();
        Factory ordinary = AoABucketItem::new;
        // An empty bucket stacks to 16 and a filled one to 1, exactly as vanilla's do.
        bind(family, BucketFamily.Kind.EMPTY, metal + "_bucket", () -> Fluids.EMPTY, 16, ordinary);
        bind(family, BucketFamily.Kind.WATER, "water_" + metal + "_bucket", () -> Fluids.WATER, 1,
                waterFactory);
        bind(family, BucketFamily.Kind.LAVA, "lava_" + metal + "_bucket", () -> Fluids.LAVA, 1,
                ordinary);
        bind(family, BucketFamily.Kind.CANDIED_WATER, "candied_water_" + metal + "_bucket",
                Buckets::candiedWater, 1, ordinary);
    }

    private static void bind(BucketFamily family, BucketFamily.Kind kind, String name,
                             Supplier<Fluid> fluid, int stackSize, Factory factory) {
        RegistryObject<Item> item = ITEMS.register(name, () -> factory.create(fluid.get(),
                new Item.Properties().tab(FarmlandBlocks.TAB).stacksTo(stackSize), family));
        family.bind(kind, item);
    }

    /**
     * The Moon Ingot water bucket's promise: no fall damage while it is in hand.
     *
     * <p>Setting the multiplier rather than cancelling keeps the landing itself - the sounds and
     * particles still play, there is simply no damage to take.
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity
                && LunarWaterBucketItem.isHeldBy((PlayerEntity) event.getEntityLiving())) {
            event.setDamageMultiplier(0.0F);
        }
    }

    private static Fluid candiedWater() {
        if (candiedWater == null) {
            candiedWater = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(CANDIED_WATER));
            if (candiedWater == null) {
                // AoA is a hard dependency and the name is checked at build time, so this only
                // happens on a version mismatch. Say so, and leave the bucket inert rather than
                // crashing the registry.
                LOGGER.warn("AoA fluid {} is missing; the candied water buckets will do nothing",
                        CANDIED_WATER);
                candiedWater = Fluids.EMPTY;
            }
        }
        return candiedWater;
    }

    private Buckets() {}
}
