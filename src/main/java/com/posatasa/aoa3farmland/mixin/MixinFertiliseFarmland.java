package com.posatasa.aoa3farmland.mixin;

import com.posatasa.aoa3farmland.FarmlandBlocks;
import com.posatasa.aoa3farmland.compat.AoASkillExperience;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes AoA's Fertilise Farmland ability work on this add-on's farmlands.
 *
 * <p>The ability (a Farming skill reward) turns vanilla farmland into fertilised farmland when the
 * player uses bone meal on it. It decides "is this farmland" with {@code state.getBlock() ==
 * Blocks.FARMLAND} - an identity check - so our farmlands, which are their own blocks, are simply
 * ignored. Rather than reimplement the ability (which would either require AoA classes at compile
 * time or hand the feature to players who never unlocked it), this hooks the ability itself: it only
 * runs when the ability would have run, and only takes over for blocks vanilla would have rejected.
 *
 * <p>Everything else is left alone, so bone meal on vanilla farmland still goes down AoA's own path.
 * For our blocks the ability's own Farming experience award is repeated here (see
 * {@link AoASkillExperience}), so converting one is worth exactly what converting vanilla farmland
 * is worth.
 */
@Mixin(targets = "net.tslat.aoa3.player.ability.FertiliseFarmland")
public abstract class MixinFertiliseFarmland {
    @Inject(method = "handleBlockInteraction", at = @At("HEAD"), cancellable = true, require = 0)
    private void aoa3farmland$fertiliseOurFarmland(PlayerInteractEvent.RightClickBlock event,
                                                   CallbackInfo ci) {
        ItemStack held = event.getItemStack();
        if (held.getItem() != Items.BONE_MEAL) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        BlockState state = world.getBlockState(pos);
        Block farmland = FarmlandBlocks.fertilisedFor(state.getBlock());
        if (farmland == null) {
            return;                                   // not ours: let AoA handle it as usual
        }
        if (!world.mayInteract(event.getPlayer(), pos)) {
            return;                                   // spawn protection and similar
        }
        BlockState fertilised = farmland.defaultBlockState()
                .setValue(FarmlandBlock.MOISTURE, state.getValue(FarmlandBlock.MOISTURE));
        world.setBlock(pos, fertilised, 3);
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            // What the ability would have awarded for a vanilla farmland.
            AoASkillExperience.awardFarmingXp((ServerPlayerEntity) event.getPlayer());
        }
        if (!event.getPlayer().isCreative()) {
            held.shrink(1);
        }
        ci.cancel();
    }
}
