package com.posatasa.aoa3farmland.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Awards AoA's Farming experience for fertilising one of this add-on's farmlands.
 *
 * <p>AoA's Fertilise Farmland ability gives a little Farming experience every time it converts a
 * block, and that award used to be the one thing lost when the conversion went through this add-on
 * instead of through AoA. The award needs AoA's skill objects, which this add-on deliberately does
 * not compile against, so it is reached by name at runtime instead:
 *
 * <pre>
 *   PlayerUtil.giveXpToPlayer(player, AoASkills.FARMING.get(),
 *           PlayerUtil.getTimeBasedXpForLevel(PlayerUtil.getLevel(player, AoASkills.FARMING.get()), 1.0F),
 *           false);
 * </pre>
 *
 * <p>which is exactly what AoA's ability does. Everything is looked up once and cached; if any of it
 * is missing (a different AoA version), the bonus is quietly skipped and logged once - the
 * conversion itself keeps working, because it does not depend on this.
 */
public final class AoASkillExperience {
    private static final Logger LOGGER = LogManager.getLogger("aoa3farmland");

    private static final String SKILLS = "net.tslat.aoa3.common.registration.custom.AoASkills";
    private static final String PLAYER_UTIL = "net.tslat.aoa3.util.PlayerUtil";
    private static final String SKILL_TYPE = "net.tslat.aoa3.player.skill.AoASkill";

    private static boolean resolved;
    private static boolean unavailable;
    private static Object farming;
    private static Method getLevel;
    private static Method getTimeBasedXp;
    private static Method giveXp;

    /** Mirrors AoA's own award; does nothing if AoA's API cannot be reached. */
    public static void awardFarmingXp(ServerPlayerEntity player) {
        if (!resolve()) {
            return;
        }
        try {
            int level = (Integer) getLevel.invoke(null, (PlayerEntity) player, farming);
            float xp = (Float) getTimeBasedXp.invoke(null, level, 1.0F);
            giveXp.invoke(null, player, farming, xp, false);
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return !unavailable;
        }
        resolved = true;
        try {
            Class<?> skills = Class.forName(SKILLS);
            Object registryObject = skills.getField("FARMING").get(null);
            farming = registryObject.getClass().getMethod("get").invoke(registryObject);
            Class<?> skillType = Class.forName(SKILL_TYPE);
            Class<?> util = Class.forName(PLAYER_UTIL);
            getLevel = util.getMethod("getLevel", PlayerEntity.class, skillType);
            getTimeBasedXp = util.getMethod("getTimeBasedXpForLevel", int.class, float.class);
            giveXp = util.getMethod("giveXpToPlayer", ServerPlayerEntity.class, skillType,
                    float.class, boolean.class);
            return true;
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    private static synchronized void disable(Throwable cause) {
        if (unavailable) {
            return;
        }
        unavailable = true;
        LOGGER.warn("Could not reach AoA's Farming experience API, so fertilising this add-on's "
                + "farmlands will not award Farming experience: {}", cause.toString());
    }

    private AoASkillExperience() {}
}
