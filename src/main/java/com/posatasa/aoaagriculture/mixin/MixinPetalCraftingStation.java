package com.posatasa.aoaagriculture.mixin;

import com.posatasa.aoaagriculture.ritual.CropCoinRitual;
import com.posatasa.aoaagriculture.ritual.MoonDirtRitual;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the moon soil ritual to AoA's petal crafting station.
 *
 * <p>The station's own {@code use} only reacts to petals and answers PASS to everything else, so
 * there is no behaviour to work around: the rituals get first look at the click, and if the click is
 * not theirs (no dye or crop in hand, or nothing to convert) it simply falls through and the station
 * behaves exactly as it always did. Two rituals live here: dye makes moon soil, AoA crops make
 * copper coins.
 *
 * <p>Targeted by class name with {@code require = 0}, like the other AoA hooks here, so a rename on
 * AoA's side costs this ritual and nothing else.
 */
@Mixin(targets = "net.tslat.aoa3.content.block.functional.utility.PetalCraftingStation")
public abstract class MixinPetalCraftingStation {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 0)
    private void aoa3agriculture$moonSoilRitual(BlockState state, World world, BlockPos pos,
                                                PlayerEntity player, Hand hand,
                                                BlockRayTraceResult hit,
                                                CallbackInfoReturnable<ActionResultType> cir) {
        ItemStack held = player.getItemInHand(hand);
        if (MoonDirtRitual.tryApply(world, pos, player, held)
                || CropCoinRitual.tryApply(world, pos, player, held)) {
            cir.setReturnValue(ActionResultType.SUCCESS);
        }
    }
}
