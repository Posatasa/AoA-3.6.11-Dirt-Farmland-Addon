package com.posatasa.aoaagriculture.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

/**
 * The Shyre water bucket: pouring costs nothing, so it is a portable water source.
 *
 * <p>Everything else about it is a normal bucket. Only the water is special; the Shyre lava and
 * candied water buckets are plain buckets, because there is nothing infinite about those.
 *
 * <p>The pour itself is vanilla's - this calls through to it and then throws its answer away, which
 * is what keeps the water. To get rid of the water the player holds the button down: that starts a
 * two-second use action, and the bucket empties when the action runs out. A shorter press only
 * pours, which is exactly the behaviour a bucket should still have.
 */
public class InfiniteWaterBucketItem extends AoABucketItem {
    /** How long the button has to be held for the bucket to empty: two seconds. */
    private static final int HOLD_TICKS = 40;

    public InfiniteWaterBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return HOLD_TICKS;
    }

    /**
     * Pours the water and keeps the bucket full.
     *
     * <p>The returned stack is deliberately discarded - vanilla's answer is the empty bucket, which
     * is the whole point of the item. {@code startUsingItem} is required: in 1.16.5 returning
     * {@code CONSUME} alone does not begin the use action, which is why {@code BowItem} calls it
     * itself too.
     */
    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        super.use(world, player, hand);
        player.startUsingItem(hand);
        return ActionResult.consume(stack);
    }

    /** Releasing after the full hold empties the bucket; a shorter press just stops the pour. */
    @Override
    public void releaseUsing(ItemStack stack, World world, LivingEntity user, int timeLeft) {
        if (timeLeft > 0 || !(user instanceof PlayerEntity)) {
            return;
        }
        Item empty = family.item(BucketFamily.Kind.EMPTY);
        if (empty == null) {
            return;
        }
        PlayerEntity player = (PlayerEntity) user;
        if (!world.isClientSide) {
            player.setItemInHand(player.getUsedItemHand(), new ItemStack(empty));
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BUCKET_EMPTY, SoundCategory.PLAYERS, 1.0F, 1.4F);
        }
    }
}
