package com.example.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Method;

/**
 * Helper to determine if the current environment (Serene Seasons + Biome Temperature)
 * is cold enough to freeze food items.
 */
public class FoodFreezingHelper {

    private static Boolean sereneSeasonsAvailable = null;
    private static Method getSeasonStateMethod = null;
    private static Method getSeasonMethod = null;

    public static boolean isFoodItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            return stack.has(DataComponents.FOOD);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isFrozenCondition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        Player player = mc.player;
        Level level = mc.level;
        BlockPos pos = player.blockPosition();

        // 1. Check Serene Seasons if present
        Boolean isWinter = checkSereneSeasonsWinter(level);
        if (Boolean.TRUE.equals(isWinter)) {
            return true;
        }

        // 2. Check biome temperature at player location
        try {
            Biome biome = level.getBiome(pos).value();
            if (biome.coldEnoughToSnow(pos) || biome.getBaseTemperature() <= 0.15f) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static Boolean checkSereneSeasonsWinter(Level level) {
        if (sereneSeasonsAvailable == null) {
            try {
                Class<?> seasonHelperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
                getSeasonStateMethod = seasonHelperClass.getMethod("getSeasonState", Level.class);
                Class<?> seasonStateClass = Class.forName("sereneseasons.api.season.ISeasonState");
                getSeasonMethod = seasonStateClass.getMethod("getSeason");
                sereneSeasonsAvailable = true;
            } catch (Throwable t) {
                sereneSeasonsAvailable = false;
            }
        }

        if (Boolean.TRUE.equals(sereneSeasonsAvailable) && getSeasonStateMethod != null && getSeasonMethod != null) {
            try {
                Object seasonState = getSeasonStateMethod.invoke(null, level);
                if (seasonState != null) {
                    Object season = getSeasonMethod.invoke(seasonState);
                    if (season != null) {
                        String name = season.toString().toUpperCase();
                        return name.contains("WINTER");
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
