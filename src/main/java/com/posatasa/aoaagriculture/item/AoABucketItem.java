package com.posatasa.aoaagriculture.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockState;
import net.minecraft.block.IBucketPickupHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Direction;
import net.minecraft.util.DrinkHelper;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A bucket of one metal: vanilla's bucket behaviour, with every item it hands back replaced by the
 * matching bucket of the same metal.
 *
 * <p>Two places in vanilla's implementation name the iron bucket directly, and both are handled
 * here:
 *
 * <ul>
 *   <li>emptying one - {@code BucketItem#getEmptySuccessItem} answers {@code minecraft:bucket};
 *   <li>filling one - the filled bucket comes from {@code fluid.getBucket()}, which is the vanilla
 *       water or lava bucket.
 * </ul>
 *
 * <p>The second one cannot be handled by looking at what vanilla returns, because in creative mode
 * vanilla does not return the filled bucket at all: {@code DrinkHelper.createFilledResult} puts it
 * straight into the player's inventory. So the filling step is done here instead, with our own
 * bucket as the thing that gets filled. Emptying is still vanilla's, with only the answer replaced.
 */
public class AoABucketItem extends BucketItem {
    private static final Logger LOGGER = LogManager.getLogger("aoaagriculture");
    /** Fluids already reported as unmatched, so the log gets one line each. */
    private static final Set<ResourceLocation> UNMATCHED = ConcurrentHashMap.newKeySet();

    protected final BucketFamily family;

    public AoABucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties);
        this.family = family;
    }

    /**
     * For fluids that are not in the registry when items are made - Forge registers fluids after
     * items, so AoA's are not. The fluid then resolves on first use; anything that would read the
     * eager field has to be overridden, which is what {@code CandiedWaterBucketItem} does.
     */
    public AoABucketItem(Supplier<? extends Fluid> content, Item.Properties properties,
                         BucketFamily family) {
        super(content, properties);
        this.family = family;
    }

    /** Vanilla hands back {@code minecraft:bucket}; this hands back this metal's empty bucket. */
    @Override
    protected ItemStack getEmptySuccessItem(ItemStack stack, PlayerEntity player) {
        Item empty = family.item(BucketFamily.Kind.EMPTY);
        if (empty == null || player.abilities.instabuild) {
            return stack;
        }
        return new ItemStack(empty);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        return getFluid() == Fluids.EMPTY
                ? fill(world, player, hand)          // filling: vanilla's answer never reaches us
                : super.use(world, player, hand);    // pouring: getEmptySuccessItem answers
    }

    /**
     * Vanilla's fill path, with our bucket as the filled one.
     *
     * <p>Kept in step with {@code BucketItem#use}: same ray trace, same Forge hook, same permission
     * checks, same statistics, sound and advancement - only the item that ends up in the player's
     * hands (or bag, in creative) changes.
     */
    protected ActionResult<ItemStack> fill(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockRayTraceResult hit = Item.getPlayerPOVHitResult(world, player,
                RayTraceContext.FluidMode.SOURCE_ONLY);
        ActionResult<ItemStack> handled = ForgeEventFactory.onBucketUse(player, world, stack, hit);
        if (handled != null) {
            return handled;
        }
        if (hit.getType() != RayTraceResult.Type.BLOCK) {
            return ActionResult.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        Direction side = hit.getDirection();
        if (!world.mayInteract(player, pos)
                || !player.mayUseItemAt(pos.relative(side), side, stack)) {
            return ActionResult.fail(stack);
        }
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IBucketPickupHandler)
                || !accepts(world.getFluidState(pos).getType())) {
            return ActionResult.fail(stack);
        }
        Fluid pickedUp = ((IBucketPickupHandler) state.getBlock()).takeLiquid(world, pos, state);
        if (pickedUp == Fluids.EMPTY) {
            return ActionResult.fail(stack);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        SoundEvent sound = pickedUp.getAttributes().getFillSound();
        if (sound == null) {
            sound = pickedUp.is(FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA
                    : SoundEvents.BUCKET_FILL;
        }
        player.playSound(sound, 1.0F, 1.0F);

        ItemStack filled = filledFor(stack, pickedUp);
        // This is the step that matters in creative: it puts the stack into the inventory instead of
        // returning it, so the stack has to be ours before it is called.
        ItemStack result = DrinkHelper.createFilledResult(stack, player, filled);
        if (!world.isClientSide) {
            CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayerEntity) player, filled);
        }
        return ActionResult.sidedSuccess(result, world.isClientSide());
    }

    /**
     * Whether this bucket will take the fluid it is looking at.
     *
     * <p>Checked before the fluid is taken out of the world, so a refusal never destroys a source.
     * The default accepts anything, which is vanilla's behaviour; the Emberstone buckets narrow it
     * to lava.
     */
    protected boolean accepts(Fluid fluid) {
        return true;
    }

    /** The stack this bucket becomes once it has taken {@code pickedUp}. */
    protected ItemStack filledFor(ItemStack stack, Fluid pickedUp) {
        BucketFamily.Kind kind = family.kindFor(pickedUp);
        if (kind != null && family.item(kind) != null) {
            return new ItemStack(family.item(kind));
        }
        // Second opinion before settling for vanilla's answer: ask the fluid which bucket declares
        // it, and match that bucket's fluid instead. A fluid block can hand back a flowing variant
        // that no bucket names, while the bucket it declares is built around the source.
        Item declared = pickedUp.getBucket();
        ItemStack mapped = family.bucketFor(new ItemStack(declared));
        if (mapped.getItem() != declared) {
            return mapped;
        }
        warnOnce(pickedUp);
        return new ItemStack(declared);
    }

    /** Says so, once per fluid, when a picked-up fluid cannot be matched to any of our buckets. */
    private void warnOnce(Fluid pickedUp) {
        if (UNMATCHED.add(pickedUp.getRegistryName())) {
            LOGGER.warn("No bucket of this metal holds {} (picked up as {}); filling gives the "
                            + "vanilla-family bucket instead",
                    pickedUp.getRegistryName(), pickedUp);
        }
    }
}
