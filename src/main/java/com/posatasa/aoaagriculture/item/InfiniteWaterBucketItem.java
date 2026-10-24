package com.posatasa.aoaagriculture.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Shyre water bucket: pouring costs nothing, so it is a portable water source.
 *
 * <p>Everything else about it is a normal bucket. Only the water is special; the Shyre lava and
 * candied water buckets are plain buckets, because there is nothing infinite about those.
 *
 * <p>The button is read as a hold rather than a click. Nothing is poured when it goes down, so a
 * long press never splashes water on the way to emptying the bucket:
 *
 * <ul>
 *   <li>released early - {@link #releaseUsing} pours, and the water stays in the bucket;
 *   <li>held for the full two seconds - {@link #finishUsingItem} empties the bucket instead.
 * </ul>
 *
 * <p>Two of these hooks are easy to get wrong, and this item did before: in 1.16.5 a completed hold
 * goes through {@code finishUsingItem} (the same path eating takes), not {@code releaseUsing}, which
 * only runs when the player lets go early. And the hold has to be started with
 * {@code startUsingItem} explicitly - returning CONSUME does not begin one, which is why
 * {@code BowItem} calls it too. The bow's pose is borrowed so the hold is visible.
 */
public class InfiniteWaterBucketItem extends AoABucketItem {
    /** How long the button has to be held: two seconds, the 40 ticks a bow also spends per second. */
    private static final int HOLD_TICKS = 40;
    private static final String TOOLTIP = "item.aoaagriculture.water_shyre_bucket.tooltip";

    public InfiniteWaterBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return HOLD_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip,
                               ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent(TOOLTIP).withStyle(TextFormatting.GRAY));
    }

    /** The bow's pull-back pose, so the hold is visible rather than silent. */
    @Override
    public UseAction getUseAnimation(ItemStack stack) {
        return UseAction.BOW;
    }

    /** Starts the hold. Nothing is poured yet; that is what keeps a long press from spilling. */
    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return ActionResult.consume(stack);
    }

    /** Released early: an ordinary pour, and the water is kept. */
    @Override
    public void releaseUsing(ItemStack stack, World world, LivingEntity user, int timeLeft) {
        if (world.isClientSide || timeLeft <= 0 || !(user instanceof PlayerEntity)) {
            return;
        }
        if (!(stack.getItem() instanceof InfiniteWaterBucketItem)) {
            return;                                  // the hold already emptied it
        }
        PlayerEntity player = (PlayerEntity) user;
        super.use(world, player, player.getUsedItemHand());
    }

    /**
     * Held for the whole two seconds: the bucket empties.
     *
     * <p>This is the natural end of a use action, so the returned stack replaces the one in hand.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, World world, LivingEntity user) {
        Item empty = family.item(BucketFamily.Kind.EMPTY);
        if (empty == null) {
            return stack;
        }
        if (!world.isClientSide) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BUCKET_EMPTY, SoundCategory.PLAYERS, 1.0F, 1.4F);
        }
        return new ItemStack(empty);
    }
}
