package com.posatasa.aoaagriculture.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The empty Emberstone bucket: a lava tank before it has anything in it.
 *
 * <p>It refuses everything except lava - water and candied water are simply not taken, and the
 * refusal happens before the fluid is lifted out of the world, so pointing it at a pool does not
 * drain it. Filling it gives the Emberstone lava bucket, which holds up to four.
 */
public class EmberstoneBucketItem extends AoABucketItem {
    private static final String TOOLTIP = "item.aoaagriculture.emberstone_bucket.tooltip";

    public EmberstoneBucketItem(Fluid content, Item.Properties properties, BucketFamily family) {
        super(content, properties, family);
    }

    @Override
    protected boolean accepts(Fluid fluid) {
        return fluid.is(FluidTags.LAVA);
    }

    @Override
    protected ItemStack filledFor(ItemStack stack, Fluid pickedUp) {
        return EmberstoneLavaBucketItem.withLava(
                new ItemStack(family.item(BucketFamily.Kind.LAVA)), 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip,
                               ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent(TOOLTIP).withStyle(TextFormatting.GRAY));
    }
}
