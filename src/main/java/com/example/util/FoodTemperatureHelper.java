package com.example.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class FoodTemperatureHelper {
    private static final ResourceLocation FIAHI_FOOD_ID = ResourceLocation.fromNamespaceAndPath("fiahi", "food");

    // Tick interval for food temperature updates: 40 ticks (2.0s at 20 TPS)
    public static final int TICK_INTERVAL = 40;

    // Temperature targets matching FIAHI food states
    public static final double TEMP_COMPLETELY_FROZEN = -100.0;
    public static final double TEMP_MOSTLY_FROZEN     = -75.0;
    public static final double TEMP_LIGHTLY_FROZEN    = -50.0;
    public static final double TEMP_FRESH             = 0.0;
    public static final double TEMP_ROTTEN            = 80.0;

    /**
     * Disables FIAHI's broken InventoryMixin by setting ForgeEventHandler.tickAfterCheck != 0.
     * On Fabric/Kilt, FIAHI's NeoForge LevelTickEvent never fires, leaving tickAfterCheck = 0 forever,
     * which caused InventoryMixin to execute every single tick (20x/sec) and desync with containers.
     */
    public static void disableFiahiBrokenTick() {
        try {
            Class<?> feh = Class.forName("com.hexagram2021.fiahi.common.ForgeEventHandler");
            java.lang.reflect.Field f = feh.getDeclaredField("tickAfterCheck");
            f.setAccessible(true);
            f.setInt(null, 1);
        } catch (Throwable ignored) {}
    }

    public static double getFrozenSpeedMultiplier() {
        try {
            Class<?> cfg = Class.forName("com.hexagram2021.fiahi.common.config.FIAHICommonConfig");
            java.lang.reflect.Field f = cfg.getField("FROZEN_SPEED_MULTIPLIER");
            Object val = f.get(null);
            if (val != null) {
                Method getM = val.getClass().getMethod("get");
                Object res = getM.invoke(val);
                if (res instanceof Number num) {
                    return num.doubleValue();
                }
            }
        } catch (Throwable ignored) {}
        return 1.0;
    }

    public static double getRottenSpeedMultiplier() {
        try {
            Class<?> cfg = Class.forName("com.hexagram2021.fiahi.common.config.FIAHICommonConfig");
            java.lang.reflect.Field f = cfg.getField("ROTTEN_SPEED_MULTIPLIER");
            Object val = f.get(null);
            if (val != null) {
                Method getM = val.getClass().getMethod("get");
                Object res = getM.invoke(val);
                if (res instanceof Number num) {
                    return num.doubleValue();
                }
            }
        } catch (Throwable ignored) {}
        return 0.75;
    }

    public static boolean isFrozenEnabled() {
        try {
            Class<?> cfg = Class.forName("com.hexagram2021.fiahi.common.config.FIAHICommonConfig");
            java.lang.reflect.Field f = cfg.getField("ENABLE_FROZEN");
            Object val = f.get(null);
            if (val != null) {
                Method getM = val.getClass().getMethod("get");
                Object res = getM.invoke(val);
                if (res instanceof Boolean b) {
                    return b;
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public static boolean isRottenEnabled() {
        try {
            Class<?> cfg = Class.forName("com.hexagram2021.fiahi.common.config.FIAHICommonConfig");
            java.lang.reflect.Field f = cfg.getField("ENABLE_ROTTEN");
            Object val = f.get(null);
            if (val != null) {
                Method getM = val.getClass().getMethod("get");
                Object res = getM.invoke(val);
                if (res instanceof Boolean b) {
                    return b;
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public static boolean isNeverFrozen(ItemStack stack) {
        try {
            Class<?> cfg = Class.forName("com.hexagram2021.fiahi.common.config.FIAHICommonConfig");
            java.lang.reflect.Field f = cfg.getField("NEVER_FROZEN_FOODS");
            Object val = f.get(null);
            if (val != null) {
                Method getM = val.getClass().getMethod("get");
                Object res = getM.invoke(val);
                if (res instanceof List<?> list) {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    return list.contains(itemId);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // =========================================================================
    // Core Environmental Temperature (Single Source of Truth)
    // =========================================================================

    /**
     * Calculates the ambient environment temperature at a given world position.
     * Cold Sweat's WorldHelper is the primary source of truth (accounting for biomes,
     * Serene Seasons, altitude, insulation, structures, and heat/cold blocks).
     */
    public static double getAmbientTemperature(Level level, BlockPos pos) {
        if (level == null || pos == null) return TEMP_FRESH;

        // 1. Direct block checks (Heat sources & intense cold blocks take immediate priority)
        double directBlockTemp = checkNearbyBlocks(level, pos);
        if (!Double.isNaN(directBlockTemp)) {
            return directBlockTemp;
        }

        // 2. Cold Sweat WorldHelper (primary world temperature provider)
        double csTemp = checkColdSweatWorld(level, pos);
        if (!Double.isNaN(csTemp)) {
            return csTemp;
        }

        // 3. Fallback: Serene Seasons seasonal temperature
        double sereneTemp = checkSereneSeasons(level, pos);
        if (!Double.isNaN(sereneTemp)) {
            return sereneTemp;
        }

        // 4. Fallback: Vanilla biome base temperature
        return checkVanillaBiome(level, pos);
    }

    /**
     * Checks immediately adjacent blocks for fire, lava, lit campfires, and ice.
     */
    private static double checkNearbyBlocks(Level level, BlockPos pos) {
        try {
            BlockPos[] checkPositions = new BlockPos[] {
                pos, pos.below(), pos.above(),
                pos.north(), pos.south(), pos.east(), pos.west()
            };
            double coldest = Double.NaN;
            for (BlockPos p : checkPositions) {
                BlockState state = level.getBlockState(p);

                // Heat sources
                if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                    return TEMP_ROTTEN;
                }
                if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
                    if (state.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT) &&
                        state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                        return 30.0; // Lit campfire provides warm thawing heat
                    }
                }
                if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) {
                    if (state.hasProperty(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT) &&
                        state.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)) {
                        return 30.0; // Lit furnace provides warmth
                    }
                }

                // Subzero cold blocks
                if (state.is(Blocks.POWDER_SNOW) || state.is(Blocks.BLUE_ICE)) {
                    coldest = TEMP_COMPLETELY_FROZEN;
                } else if (state.is(Blocks.PACKED_ICE)) {
                    if (Double.isNaN(coldest) || TEMP_MOSTLY_FROZEN < coldest) coldest = TEMP_MOSTLY_FROZEN;
                } else if (state.is(Blocks.ICE)) {
                    if (Double.isNaN(coldest) || TEMP_LIGHTLY_FROZEN < coldest) coldest = TEMP_LIGHTLY_FROZEN;
                }
            }
            if (!Double.isNaN(coldest)) {
                return coldest;
            }
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    /**
     * Queries Cold Sweat's WorldHelper.getTemperatureAt for world temperature.
     * In Cold Sweat, values < 0 indicate below-freezing conditions.
     */
    private static double checkColdSweatWorld(Level level, BlockPos pos) {
        try {
            Class<?> whClass = Class.forName("com.momosoftworks.coldsweat.util.world.WorldHelper");
            Method m = whClass.getMethod("getTemperatureAt", Level.class, BlockPos.class);
            Object val = m.invoke(null, level, pos);
            if (val instanceof Number num) {
                double raw = num.doubleValue();
                // When DummyPlayer has no capability on Fabric/Kilt, getTemperatureAt returns exactly 0.0.
                // Do not treat 0.0 as freezing!
                if (Math.abs(raw) < 0.0001) {
                    return Double.NaN;
                }
                // Cold Sweat scale (MC units: 0.0 = freezing point 32°F / 0°C, 1.0 = 77°F / 25°C, 1.51 = 100°F / 38°C):
                if (raw <= -0.5) {
                    return TEMP_COMPLETELY_FROZEN;
                } else if (raw <= -0.2) {
                    return TEMP_MOSTLY_FROZEN;
                } else if (raw < 0.0) {
                    return TEMP_LIGHTLY_FROZEN;
                } else if (raw >= 1.3) {
                    return TEMP_ROTTEN;
                } else {
                    return TEMP_FRESH;
                }
            }
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    /**
     * Queries Serene Seasons when Cold Sweat is unavailable.
     */
    private static double checkSereneSeasons(Level level, BlockPos pos) {
        try {
            Class<?> helperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
            Method getStateMethod = null;
            for (Method m : helperClass.getMethods()) {
                if (m.getName().equals("getSeasonState") && m.getParameterCount() == 1) {
                    getStateMethod = m;
                    break;
                }
            }
            if (getStateMethod != null) {
                Object seasonState = getStateMethod.invoke(null, level);
                if (seasonState != null) {
                    // Check if it is cold enough to snow first
                    try {
                        Class<?> seasonHooks = Class.forName("sereneseasons.season.SeasonHooks");
                        for (Method hm : seasonHooks.getMethods()) {
                            if (hm.getName().startsWith("coldEnoughToSnow") && hm.getParameterCount() >= 2) {
                                Object result = hm.getParameterCount() == 2
                                    ? hm.invoke(null, level, pos)
                                    : hm.invoke(null, level, level.getBiome(pos), pos);
                                if (Boolean.TRUE.equals(result)) {
                                    return TEMP_COMPLETELY_FROZEN; // Snow/freezing conditions
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    Method getSeasonMethod = seasonState.getClass().getMethod("getSeason");
                    Object seasonObj = getSeasonMethod.invoke(seasonState);
                    String seasonName = seasonObj != null ? seasonObj.toString() : "";

                    if ("WINTER".equals(seasonName)) {
                        return TEMP_LIGHTLY_FROZEN; // Winter chill
                    } else if ("SUMMER".equals(seasonName)) {
                        return TEMP_ROTTEN; // Summer heat -> food melts rapidly
                    } else {
                        return TEMP_FRESH; // Spring / Autumn -> fresh, thawing
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    /**
     * Vanilla Biome Fallback when no temperature mod is available.
     */
    private static double checkVanillaBiome(Level level, BlockPos pos) {
        try {
            double biomeBase = level.getBiome(pos).value().getBaseTemperature();
            if (biomeBase < 0.15) {
                return TEMP_COMPLETELY_FROZEN;
            } else if (biomeBase > 1.2) {
                return TEMP_ROTTEN;
            }
        } catch (Throwable ignored) {}
        return TEMP_FRESH;
    }

    // =========================================================================
    // Player Ambient Temperature
    // =========================================================================

    /**
     * Calculates ambient temperature for player inventory food.
     * Uses player-specific Cold Sweat traits, held heat sources, inventory coolers,
     * and falls back to environmental world temperature.
     */
    public static double getAmbientTemperatureForPlayer(Player player) {
        if (player == null) return TEMP_FRESH;

        // 1. Powder snow / vanilla freeze ticks
        if (player.getTicksFrozen() > 0) {
            return TEMP_COMPLETELY_FROZEN;
        }

        // 2. Ice in inventory acts as an intentional portable cooler
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                if (s.is(Items.BLUE_ICE) || s.is(Items.PACKED_ICE) || s.is(Items.ICE)) {
                    return TEMP_COMPLETELY_FROZEN;
                }
            }
        }

        // 3. Handheld heat sources provide warmth buffer against ambient cold
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean holdingHeat = isHeatSource(mainHand) || isHeatSource(offHand);

        // 4. Cold Sweat Trait checks (for the real player)
        double ambient = Double.NaN;
        try {
            Class<?> tempClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
            Class<?> traitClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
            Method getMethod = null;
            for (Method m : tempClass.getMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() == 2) {
                    getMethod = m;
                    break;
                }
            }
            if (getMethod != null) {
                Object bodyTrait = null;
                Object worldTrait = null;
                for (Object ec : traitClass.getEnumConstants()) {
                    if ("BODY".equals(ec.toString())) bodyTrait = ec;
                    if ("WORLD".equals(ec.toString())) worldTrait = ec;
                }

                // Check Body Temp first for extreme medical conditions (-100 to +100 scale in Cold Sweat)
                if (bodyTrait != null) {
                    Object val = getMethod.invoke(null, player, bodyTrait);
                    if (val instanceof Number n) {
                        double bodyTemp = n.doubleValue();
                        // Severe hypothermia: player is shivering to death (damage starts at -100)
                        if (bodyTemp <= -75.0) {
                            ambient = TEMP_COMPLETELY_FROZEN;
                        } else if (bodyTemp >= 75.0) {
                            ambient = TEMP_ROTTEN;
                        }
                    }
                }

                // If body is not in extreme fever/freeze, check player's Cold Sweat WORLD temperature (MC units)
                if (Double.isNaN(ambient) && worldTrait != null) {
                    Object wVal = getMethod.invoke(null, player, worldTrait);
                    if (wVal instanceof Number wn) {
                        double worldTemp = wn.doubleValue();
                        // Only use if worldTemp is non-zero (0.0 is uninitialized/failure default)
                        if (Math.abs(worldTemp) > 0.0001) {
                            if (worldTemp <= -0.5) {
                                ambient = TEMP_COMPLETELY_FROZEN;
                            } else if (worldTemp <= -0.2) {
                                ambient = TEMP_MOSTLY_FROZEN;
                            } else if (worldTemp < 0.0) {
                                ambient = TEMP_LIGHTLY_FROZEN;
                            } else if (worldTemp >= 1.3) {
                                ambient = TEMP_ROTTEN;
                            } else {
                                ambient = TEMP_FRESH;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 5. Fallback to environmental world temperature at player's position
        if (Double.isNaN(ambient)) {
            ambient = getAmbientTemperature(player.level(), player.blockPosition());
        }

        // Holding heat buffers against cold, thawing frozen food to fresh
        if (holdingHeat && ambient < 0) {
            ambient = TEMP_FRESH;
        }

        return ambient;
    }

    private static boolean isHeatSource(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) ||
               stack.is(Items.LANTERN) || stack.is(Items.SOUL_LANTERN) ||
               stack.is(Items.CAMPFIRE) || stack.is(Items.SOUL_CAMPFIRE) ||
               stack.is(Items.LAVA_BUCKET);
    }

    // =========================================================================
    // Ticking Logic (Inventory, Containers, Food Stacks)
    // =========================================================================

    /**
     * Ticks food items in a player's inventory (hands, hotbar, inventory, offhand).
     */
    public static void tickInventory(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel)) return;

        disableFiahiBrokenTick();

        double ambientTemp = getAmbientTemperatureForPlayer(player);
        boolean changed = false;
        Inventory inv = player.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                double before = getFoodTemperature(stack);
                tickFood(stack, ambientTemp);
                double after = getFoodTemperature(stack);
                if (Math.abs(after - before) > 0.1) {
                    changed = true;
                }
            }
        }

        if (changed && player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
            if (sp.containerMenu != null && sp.containerMenu != sp.inventoryMenu) {
                sp.containerMenu.broadcastChanges();
            }
        }
    }

    /**
     * Ticks all containers (chests, barrels, shulker boxes, etc.) in a ticking chunk.
     * Uses the EXACT same ambient temperature calculation as player inventory.
     */
    public static void tickChunkContainers(ServerLevel level, LevelChunk chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        disableFiahiBrokenTick();

        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (!(be instanceof Container container)) continue;

            // Skip iceboxes and boilers (handled natively by Cold Sweat / FIAHI)
            ResourceLocation beType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (beType != null) {
                String path = beType.getPath();
                if (path.contains("icebox") || path.contains("boiler")) {
                    continue;
                }
            }

            double ambientTemp = getAmbientTemperature(level, be.getBlockPos());

            // Check if container has ice items (acts as a freezer)
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    if (s.is(Items.BLUE_ICE) || s.is(Items.PACKED_ICE) || s.is(Items.ICE)) {
                        ambientTemp = TEMP_COMPLETELY_FROZEN;
                        break;
                    }
                }
            }

            boolean changed = false;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                    double before = getFoodTemperature(stack);
                    tickFood(stack, ambientTemp);
                    double after = getFoodTemperature(stack);
                    if (Math.abs(after - before) > 0.1) {
                        changed = true;
                    }
                }
            }

            if (changed) {
                be.setChanged();
                container.setChanged();

                // Sync open container menu immediately
                for (ServerPlayer sp : level.players()) {
                    if (sp.containerMenu != null && sp.containerMenu != sp.inventoryMenu) {
                        for (Slot slot : sp.containerMenu.slots) {
                            if (slot.container == container) {
                                sp.containerMenu.broadcastChanges();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Ticks a single food stack towards targetTemp.
     *
     * PRESERVATION IN COLD ENVIRONMENTS:
     * In a cold environment (targetTemp < 0), already frozen food NEVER warms up.
     * Cold ambient air preserves cold food. It only thaws if the environment is
     * above freezing (targetTemp >= 0).
     */
    public static void tickFood(ItemStack stack, double targetTemp) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) return;

        // If frozen mechanic is disabled, don't freeze
        if (targetTemp < 0 && (!isFrozenEnabled() || isNeverFrozen(stack))) {
            return;
        }
        // If rotten mechanic is disabled, cap targetTemp at fresh so frozen food can still thaw!
        if (!isRottenEnabled() && targetTemp > 0) {
            targetTemp = TEMP_FRESH;
        }

        double currentTemp = getFoodTemperature(stack);

        // In freezing weather (targetTemp < 0), already frozen food never warms up!
        if (currentTemp < 0 && targetTemp < 0) {
            targetTemp = Math.min(targetTemp, currentTemp);
        }

        double diff = targetTemp - currentTemp;
        if (Math.abs(diff) < 0.5) return;

        double step;
        if (currentTemp < 0 && diff > 0) {
            // FOOD IS THAWING / MELTING!
            // When ambient is hot (targetTemp > 0), melt very quickly (5.0 per tick).
            // When ambient is temperate (targetTemp == 0), melt steadily (2.5 per tick).
            double thawBase = targetTemp > 0 ? 5.0 : 2.5;
            double thawSpeed = thawBase * getFrozenSpeedMultiplier();
            step = Math.min(diff, thawSpeed);
        } else if (diff < 0) {
            // FOOD IS COOLING / FREEZING
            double freezeSpeed = 2.0 * getFrozenSpeedMultiplier();
            step = -Math.min(Math.abs(diff), freezeSpeed);
        } else {
            // FOOD IS WARMING / ROTTING (currentTemp >= 0 and diff > 0)
            double rotSpeed = 1.0 * getRottenSpeedMultiplier();
            step = Math.min(diff, rotSpeed);
        }

        double newTemp = currentTemp + step;

        // If thawing towards fresh (targetTemp == 0.0), snap to fresh when passing 0
        if (currentTemp < 0 && newTemp > 0 && targetTemp == 0.0) {
            newTemp = 0.0;
        }

        setFoodTemperature(stack, newTemp);
    }

    // =========================================================================
    // Food Temperature Getters & Setters
    // =========================================================================

    @SuppressWarnings("unchecked")
    public static double getFoodTemperature(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;

        try {
            DataComponentType<?> compType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(FIAHI_FOOD_ID);
            if (compType != null) {
                Integer val = (Integer) stack.get(compType);
                if (val != null) {
                    return val.doubleValue();
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> iStackClass = Class.forName("com.hexagram2021.fiahi.common.item.capability.IFrozenRottenItemStack");
            if (iStackClass.isInstance(stack)) {
                Method getFood = iStackClass.getMethod("fiahi$getFrozenRottenFood");
                Object foodObj = getFood.invoke(stack);
                if (foodObj != null) {
                    Method getTemp = foodObj.getClass().getMethod("getTemperature");
                    return ((Number) getTemp.invoke(foodObj)).doubleValue();
                }
            }
        } catch (Throwable ignored) {}

        return 0.0;
    }

    @SuppressWarnings("unchecked")
    public static void setFoodTemperature(ItemStack stack, double temp) {
        if (stack.isEmpty()) return;

        int intTemp = (int) Math.round(temp);
        if (intTemp < -125) intTemp = -125;
        if (intTemp > 125) intTemp = 125;

        // 1. Set Vanilla Data Component (fiahi:food)
        try {
            DataComponentType<?> compType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(FIAHI_FOOD_ID);
            if (compType != null) {
                stack.set((DataComponentType<Integer>) compType, intTemp);
            }
        } catch (Throwable ignored) {}

        // 2. Set FIAHI capability internal state & sync tag
        try {
            Class<?> iStackClass = Class.forName("com.hexagram2021.fiahi.common.item.capability.IFrozenRottenItemStack");
            if (iStackClass.isInstance(stack)) {
                Method getFood = iStackClass.getMethod("fiahi$getFrozenRottenFood");
                Object foodObj = getFood.invoke(stack);
                if (foodObj != null) {
                    Method setTemp = foodObj.getClass().getMethod("setTemperature", double.class);
                    setTemp.invoke(foodObj, (double) intTemp);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String getTempStateDescription(int temp) {
        if (temp <= -100) return "Completely Frozen (Level 3)";
        if (temp <= -75) return "Mostly Frozen (Level 2)";
        if (temp <= -50) return "Lightly Frozen (Level 1)";
        if (temp >= 75) return "Completely Rotten";
        if (temp >= 50) return "Mostly Rotten";
        if (temp >= 25) return "Lightly Rotten";
        return "Fresh";
    }

    // =========================================================================
    // Commands (/freezefood, /freezefood check, etc.)
    // =========================================================================

    public static int executeFreezeCommand(CommandSourceStack source, ServerPlayer player, int temp) {
        ItemStack held = player.getMainHandItem();
        String desc = getTempStateDescription(temp);

        if (!held.isEmpty() && held.has(DataComponents.FOOD)) {
            setFoodTemperature(held, temp);
            player.inventoryMenu.broadcastChanges();
            source.sendSuccess(() -> Component.literal(
                "§b[HypothermiaCore] Set held " + held.getHoverName().getString() + " temperature to " + temp + " (" + desc + ")!"
            ), false);
            return 1;
        } else {
            ItemStack beef = new ItemStack(Items.COOKED_BEEF);
            setFoodTemperature(beef, temp);
            if (!player.getInventory().add(beef)) {
                player.drop(beef, false);
            }
            player.inventoryMenu.broadcastChanges();
            source.sendSuccess(() -> Component.literal(
                "§b[HypothermiaCore] Gave 1x Cooked Beef with temperature " + temp + " (" + desc + ")!"
            ), false);
            return 1;
        }
    }

    public static int executeCheckCommand(CommandSourceStack source, ServerPlayer player) {
        double ambient = getAmbientTemperatureForPlayer(player);
        BlockPos pos = player.blockPosition();
        String biomeKey = player.level().getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("unknown");
        float biomeBase = player.level().getBiome(pos).value().getBaseTemperature();

        // Check Serene Seasons
        String seasonInfo = "Not loaded";
        try {
            Class<?> helperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
            Method m = helperClass.getMethod("getSeasonState", Level.class);
            Object state = m.invoke(null, player.level());
            if (state != null) {
                Method getSub = state.getClass().getMethod("getSubSeason");
                seasonInfo = String.valueOf(getSub.invoke(state));
            }
        } catch (Throwable ignored) {}

        // Check Cold Sweat Player traits
        String csInfo = "Not loaded";
        try {
            Class<?> tempClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
            Class<?> traitClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
            Method getM = tempClass.getMethod("get", net.minecraft.world.entity.LivingEntity.class, traitClass);
            Object bodyTrait = null;
            Object worldTrait = null;
            for (Object ec : traitClass.getEnumConstants()) {
                if ("BODY".equals(ec.toString())) bodyTrait = ec;
                if ("WORLD".equals(ec.toString())) worldTrait = ec;
            }
            double csBody = bodyTrait != null ? ((Number) getM.invoke(null, player, bodyTrait)).doubleValue() : 0.0;
            double csWorld = worldTrait != null ? ((Number) getM.invoke(null, player, worldTrait)).doubleValue() : 0.0;
            csInfo = String.format("Body=%.1f, World=%.2f", csBody, csWorld);
        } catch (Throwable ignored) {}

        final String fSeason = seasonInfo;
        final String fCs = csInfo;
        source.sendSuccess(() -> Component.literal("§6=== [HypothermiaCore] Temperature Diagnostic ==="), false);
        source.sendSuccess(() -> Component.literal("§7Position: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " | Biome: §e" + biomeKey + " §7(base=" + biomeBase + ")"), false);
        source.sendSuccess(() -> Component.literal("§7Serene Seasons: §a" + fSeason + " §7| Cold Sweat: §b" + fCs), false);
        source.sendSuccess(() -> Component.literal("§7Calculated Food Target Temp: §b" + String.format("%.1f", ambient) + " (" + getTempStateDescription((int) Math.round(ambient)) + ")"), false);

        Inventory inv = player.getInventory();
        int foodCount = 0;
        List<String> foodItems = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                foodCount++;
                double temp = getFoodTemperature(stack);
                if (foodItems.size() < 5) {
                    foodItems.add(stack.getHoverName().getString() + ": " + (int) Math.round(temp) + "°");
                }
            }
        }
        final int finalFoodCount = foodCount;
        source.sendSuccess(() -> Component.literal("§7Player Inventory Food: §a" + finalFoodCount + (foodItems.isEmpty() ? "" : " §8[" + String.join(", ", foodItems) + "]")), false);

        int containerCount = 0;
        Level level = player.level();
        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos p = pos.offset(x, y, z);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof Container container) {
                        containerCount++;
                        double cAmbient = getAmbientTemperature(level, p);
                        List<String> cFoods = new ArrayList<>();
                        for (int s = 0; s < container.getContainerSize(); s++) {
                            ItemStack cStack = container.getItem(s);
                            if (!cStack.isEmpty() && cStack.has(DataComponents.FOOD)) {
                                cFoods.add(cStack.getHoverName().getString() + ": " + (int) Math.round(getFoodTemperature(cStack)) + "°");
                            }
                        }
                        String typeName = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getPath();
                        source.sendSuccess(() -> Component.literal(
                            "§7Container §e" + typeName + " §7at §f" + p.getX() + "," + p.getY() + "," + p.getZ() +
                            " §7| Ambient: §b" + String.format("%.1f", cAmbient) + "° §7| Food: §a" + cFoods.size() +
                            (cFoods.isEmpty() ? "" : " §8[" + String.join(", ", cFoods) + "]")
                        ), false);
                    }
                }
            }
        }
        if (containerCount == 0) {
            source.sendSuccess(() -> Component.literal("§7Nearby Containers: §8None found within 4 blocks"), false);
        }

        return 1;
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("freezefood")
            .requires(source -> true)
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return executeFreezeCommand(ctx.getSource(), player, (int) TEMP_COMPLETELY_FROZEN);
            })
            .then(Commands.literal("check")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return executeCheckCommand(ctx.getSource(), player);
                })
            )
            .then(Commands.literal("status")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return executeCheckCommand(ctx.getSource(), player);
                })
            )
            .then(Commands.literal("fresh")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return executeFreezeCommand(ctx.getSource(), player, (int) TEMP_FRESH);
                })
            )
            .then(Commands.literal("frozen")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return executeFreezeCommand(ctx.getSource(), player, (int) TEMP_COMPLETELY_FROZEN);
                })
            )
            .then(Commands.literal("rotten")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return executeFreezeCommand(ctx.getSource(), player, (int) TEMP_ROTTEN);
                })
            )
            .then(Commands.argument("temperature", IntegerArgumentType.integer(-125, 125))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int temp = IntegerArgumentType.getInteger(ctx, "temperature");
                    return executeFreezeCommand(ctx.getSource(), player, temp);
                })
            )
            .then(Commands.literal("give")
                .then(Commands.argument("item", ItemArgument.item(registryAccess))
                    .then(Commands.argument("temperature", IntegerArgumentType.integer(-125, 125))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemInput itemInput = ItemArgument.getItem(ctx, "item");
                            int temp = IntegerArgumentType.getInteger(ctx, "temperature");
                            ItemStack stack = itemInput.createItemStack(1, false);
                            setFoodTemperature(stack, temp);
                            if (!player.getInventory().add(stack)) {
                                player.drop(stack, false);
                            }
                            player.inventoryMenu.broadcastChanges();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§b[HypothermiaCore] Gave 1x " + stack.getHoverName().getString() + " with temp " + temp + " (" + getTempStateDescription(temp) + ")!"
                            ), false);
                            return 1;
                        })
                    )
                )
            )
        );

        dispatcher.register(Commands.literal("freeze_food")
            .requires(source -> true)
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return executeFreezeCommand(ctx.getSource(), player, (int) TEMP_COMPLETELY_FROZEN);
            })
            .then(Commands.literal("check")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return executeCheckCommand(ctx.getSource(), player);
                })
            )
            .then(Commands.argument("temperature", IntegerArgumentType.integer(-125, 125))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int temp = IntegerArgumentType.getInteger(ctx, "temperature");
                    return executeFreezeCommand(ctx.getSource(), player, temp);
                })
            )
        );
    }
}
