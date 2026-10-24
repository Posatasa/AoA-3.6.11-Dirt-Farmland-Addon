package com.posatasa.aoaagriculture.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockState;
import net.minecraft.block.IBucketPickupHandler;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Emberstone lava bucket: a tank that holds four buckets of lava.
 *
 * <p>Looked at a lava source it fills, one bucket at a time, until it is full; anywhere else it
 * pours, one lava at a time, and becomes the empty Emberstone bucket when the last one goes. Water
 * and candied water are refused in both directions, so it can neither be filled with them nor spill
 * them.
 *
 * <p>How much is inside lives in the stack's NBT and is shown in the tooltip. It is not shown as a
 * durability bar: in 1.16.5 that is the only bar an item can draw, and it draws nothing when the
 * item is undamaged - so a full tank would have looked like an ordinary item.
 */
public class EmberstoneLavaBucketItem extends AoABucketItem {
    /** How many buckets of lava this holds. */
    public static final int CAPACITY = 4;
    private static final String LAVA_TAG = "lava";
    private static final String TOOLTIP = "item.aoaagriculture.lava_emberstone_bucket.tooltip";

    public EmberstoneLavaBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    /** How much lava this stack holds. A stack without the tag is one full single bucket. */
    public static int storedLava(ItemStack stack) {
        CompoundNBT tag = stack.getTag();
        if (tag == null || !tag.contains(LAVA_TAG)) {
            return 1;
        }
        return Math.max(0, Math.min(CAPACITY, tag.getInt(LAVA_TAG)));
    }

    /** A copy of {@code stack} holding {@code lava}. */
    public static ItemStack withLava(ItemStack stack, int lava) {
        ItemStack result = stack.copy();
        result.getOrCreateTag().putInt(LAVA_TAG, lava);
        return result;
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int stored = storedLava(stack);

        if (stored < CAPACITY) {
            ActionResult<ItemStack> toppedUp = topUp(world, player, hand, stored);
            if (toppedUp != null) {
                return toppedUp;
            }
        }

        ActionResult<ItemStack> poured = super.use(world, player, hand);
        if (!poured.getResult().consumesAction()) {
            return poured;
        }
        int left = stored - 1;
        ItemStack result = left <= 0
                ? new ItemStack(family.item(BucketFamily.Kind.EMPTY))
                : withLava(stack, left);
        return new ActionResult<>(poured.getResult(), result);
    }

    /**
     * Takes one lava out of the source under the crosshair and puts it into this stack.
     *
     * <p>Deliberately not routed through {@code AoABucketItem#fill}: that path swaps the stack for a
     * different item via {@code DrinkHelper.createFilledResult}, and for a tank that would hand out
     * a second stack in creative rather than topping up the one in hand. Returns null when there is
     * nothing to take, so the caller falls through to pouring.
     */
    @Nullable
    private ActionResult<ItemStack> topUp(World world, PlayerEntity player, Hand hand, int stored) {
        ItemStack stack = player.getItemInHand(hand);
        BlockRayTraceResult hit = Item.getPlayerPOVHitResult(world, player,
                RayTraceContext.FluidMode.SOURCE_ONLY);
        ActionResult<ItemStack> handled = ForgeEventFactory.onBucketUse(player, world, stack, hit);
        if (handled != null) {
            return handled;
        }
        if (hit.getType() != RayTraceResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        Direction side = hit.getDirection();
        if (!world.mayInteract(player, pos)
                || !player.mayUseItemAt(pos.relative(side), side, stack)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof IBucketPickupHandler)
                || !world.getFluidState(pos).getType().is(FluidTags.LAVA)) {
            return null;                      // checked before taking, so nothing is drained
        }
        Fluid taken = ((IBucketPickupHandler) state.getBlock()).takeLiquid(world, pos, state);
        if (taken == Fluids.EMPTY) {
            return null;
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        SoundEvent sound = taken.getAttributes().getFillSound();
        player.playSound(sound == null ? SoundEvents.BUCKET_FILL_LAVA : sound, 1.0F, 1.0F);

        ItemStack filled = withLava(stack, stored + 1);
        if (!world.isClientSide) {
            CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayerEntity) player, filled);
        }
        return ActionResult.sidedSuccess(filled, world.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip,
                               ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent(TOOLTIP, storedLava(stack), CAPACITY)
                .withStyle(TextFormatting.GRAY));
    }
}
