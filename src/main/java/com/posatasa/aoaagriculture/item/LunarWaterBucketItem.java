package com.posatasa.aoaagriculture.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Moon Ingot water bucket: an ordinary bucket to use, a soft landing to carry.
 *
 * <p>While it is in either hand the player takes no fall damage - see the listener in
 * {@code Buckets}, which is where the immunity lives rather than in the item, so it also covers a
 * player who is falling rather than using the bucket.
 *
 * <p>Only the water bucket does this; the other Moon Ingot buckets are plain buckets, and the
 * tooltip is what tells the player this one is different.
 */
public class LunarWaterBucketItem extends AoABucketItem {
    private static final String TOOLTIP = "item.aoaagriculture.water_lunar_bucket.tooltip";

    public LunarWaterBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip,
                               ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent(TOOLTIP).withStyle(TextFormatting.GRAY));
    }

    /** True when this player is holding the Moon Ingot water bucket in either hand. */
    public static boolean isHeldBy(PlayerEntity player) {
        return player.getMainHandItem().getItem() instanceof LunarWaterBucketItem
                || player.getOffhandItem().getItem() instanceof LunarWaterBucketItem;
    }
}
