package com.posatasa.aoaagriculture;

import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

/**
 * The sound and particles both petal crafting station rituals share.
 *
 * <p>The sound is the station's own, looked up by registry name so nothing AoA-side is linked; it
 * falls back to a vanilla one if AoA ever renames it. That fallback is why this lives in one place:
 * a missing sound should not be a thing each ritual gets wrong separately.
 */
final class StationFx {
    private static final String STATION_SOUND = "aoa3:block.petal_crafting_station.use";
    private static final int PARTICLES = 8;

    private static boolean resolved;
    private static SoundEvent sound;

    static void play(World world, BlockPos pos, float pitch) {
        if (!resolved) {
            resolved = true;
            sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(STATION_SOUND));
            if (sound == null) {
                sound = SoundEvents.ENCHANTMENT_TABLE_USE;
            }
        }
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0F, pitch);

        Random random = world.random;
        for (int i = 0; i < PARTICLES; i++) {
            world.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.2D + random.nextDouble() * 0.6D,
                    pos.getY() + 0.6D + random.nextDouble() * 0.4D,
                    pos.getZ() + 0.2D + random.nextDouble() * 0.6D,
                    0.0D, 0.0D, 0.0D);
        }
    }

    private StationFx() {}
}
