package com.posatasa.aoaagriculture.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

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
 * <p>Nothing else needs touching, because the fluid a bucket carries is passed in from the family
 * that owns it: the content decides what is placed, and the family decides what is handed back.
 */
public class AoABucketItem extends BucketItem {
    private final BucketFamily family;

    public AoABucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
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

    /** Vanilla hands back the iron bucket for the fluid it picked up; this maps it to this metal. */
    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ActionResult<ItemStack> result = super.use(world, player, hand);
        ItemStack filled = result.getObject();
        ItemStack mapped = family.bucketFor(filled);
        return mapped == filled ? result : new ActionResult<>(result.getResult(), mapped);
    }
}
