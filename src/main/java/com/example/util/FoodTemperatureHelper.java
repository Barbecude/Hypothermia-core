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

    /**
     * Ticks food items in a player's inventory (hands, hotbar, inventory, offhand).
     * Does NOT tick container menus to prevent double-ticking open containers.
     */
    public static void tickInventory(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel)) return;

        disableFiahiBrokenTick();

        double ambientTemp = getAmbientTemperatureForPlayer(player);

        boolean changed = false;
        Inventory inv = player.getInventory();

        // Tick all player inventory items (hotbar, hands, main inventory, offhand)
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

        // Broadcast inventory changes immediately to client (both inventoryMenu and any open containerMenu)
        if (changed && player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
            if (sp.containerMenu != null && sp.containerMenu != sp.inventoryMenu) {
                sp.containerMenu.broadcastChanges();
            }
        }
    }

    /**
     * Ticks all containers (chests, barrels, shulker boxes, etc.) in a ticking chunk.
     * Single source of truth for containers: ensures identical speed whether open or closed.
     */
    public static void tickChunkContainers(ServerLevel level, LevelChunk chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        disableFiahiBrokenTick();

        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (!(be instanceof Container container)) continue;

            // Skip iceboxes and boilers (handled by Cold Sweat / FIAHI)
            ResourceLocation beType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            if (beType != null) {
                String path = beType.getPath();
                if (path.contains("icebox") || path.contains("boiler")) {
                    continue;
                }
            }

            double ambientTemp = getAmbientTemperature(level, be.getBlockPos());

            // Check if container contains ice items (acts as a freezer)
            double containerCooler = Double.NaN;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    if (s.is(Items.BLUE_ICE)) {
                        containerCooler = -100.0;
                        break;
                    } else if (s.is(Items.PACKED_ICE)) {
                        if (Double.isNaN(containerCooler) || -75.0 < containerCooler) containerCooler = -75.0;
                    } else if (s.is(Items.ICE)) {
                        if (Double.isNaN(containerCooler) || -50.0 < containerCooler) containerCooler = -50.0;
                    } else if (s.is(Items.SNOW_BLOCK)) {
                        if (Double.isNaN(containerCooler) || -35.0 < containerCooler) containerCooler = -35.0;
                    }
                }
            }
            if (!Double.isNaN(containerCooler)) {
                ambientTemp = containerCooler;
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

                // If any player has this container open on screen, broadcast changes immediately
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
     * Uniform rate of 5.0 degrees per 20 ticks (1 second), modified by FIAHI speed multiplier.
     */
    public static void tickFood(ItemStack stack, double targetTemp) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) return;

        if (targetTemp < 0 && (!isFrozenEnabled() || isNeverFrozen(stack))) {
            return;
        }
        if (targetTemp > 0 && !isRottenEnabled()) {
            return;
        }

        double currentTemp = getFoodTemperature(stack);
        double diff = targetTemp - currentTemp;

        // If very close to target, stop adjusting
        if (Math.abs(diff) < 0.5) return;

        double speedMultiplier = diff < 0 ? getFrozenSpeedMultiplier() : getRottenSpeedMultiplier();
        double baseStep = 3.0 * speedMultiplier;
        double step = Math.signum(diff) * Math.min(Math.abs(diff), baseStep);
        double newTemp = currentTemp + step;

        setFoodTemperature(stack, newTemp);
    }

    /**
     * Calculates ambient temperature for a player.
     * Integrates body heat insulation, handheld warmth, and graduated Cold Sweat thresholds.
     */
    public static double getAmbientTemperatureForPlayer(Player player) {
        if (player == null) return 0.0;

        // 1. Powder snow / vanilla freeze ticks (player is actively freezing in powder snow)
        if (player.getTicksFrozen() > 0) {
            return -100.0;
        }

        // 2. Inventory contains ice items (acts as an intentional cooler)
        Inventory inv = player.getInventory();
        double invCoolerTemp = Double.NaN;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                if (s.is(Items.BLUE_ICE)) {
                    invCoolerTemp = -100.0;
                    break;
                } else if (s.is(Items.PACKED_ICE)) {
                    if (Double.isNaN(invCoolerTemp) || -75.0 < invCoolerTemp) invCoolerTemp = -75.0;
                } else if (s.is(Items.ICE)) {
                    if (Double.isNaN(invCoolerTemp) || -60.0 < invCoolerTemp) invCoolerTemp = -60.0;
                }
            }
        }
        if (!Double.isNaN(invCoolerTemp)) {
            return invCoolerTemp;
        }

        // 3. Ambient temperature at player's location
        double posAmbient = getAmbientTemperature(player.level(), player.blockPosition());

        // Heat source held in hand (Torch, Lantern, Lava Bucket) provides warmth to pocket food
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean holdingHeat = mainHand.is(Items.TORCH) || mainHand.is(Items.SOUL_TORCH) ||
                              mainHand.is(Items.LANTERN) || mainHand.is(Items.SOUL_LANTERN) ||
                              mainHand.is(Items.LAVA_BUCKET) ||
                              offHand.is(Items.TORCH) || offHand.is(Items.SOUL_TORCH) ||
                              offHand.is(Items.LANTERN) || offHand.is(Items.SOUL_LANTERN) ||
                              offHand.is(Items.LAVA_BUCKET);

        // 4. Cold Sweat integration (player body temperature & world temperature)
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
                Object worldTrait = null;
                Object bodyTrait = null;
                for (Object ec : traitClass.getEnumConstants()) {
                    if ("WORLD".equals(ec.toString())) worldTrait = ec;
                    if ("BODY".equals(ec.toString())) bodyTrait = ec;
                }

                double worldTemp = Double.NaN;
                double bodyTemp = Double.NaN;
                if (worldTrait != null) {
                    Object val = getMethod.invoke(null, player, worldTrait);
                    if (val instanceof Number n) worldTemp = n.doubleValue();
                }
                if (bodyTrait != null) {
                    Object val = getMethod.invoke(null, player, bodyTrait);
                    if (val instanceof Number n) bodyTemp = n.doubleValue();
                }

                double target = posAmbient;

                if (!Double.isNaN(worldTemp)) {
                    if (worldTemp <= -20.0 || (worldTemp >= -2.0 && worldTemp <= -0.2)) {
                        target = -100.0; // Extreme freezing blizzard (Level 3 Frozen)
                    } else if (worldTemp <= -10.0 || (worldTemp > -0.2 && worldTemp <= 0.05)) {
                        target = -75.0;  // Deep subzero (Level 2 Frozen)
                    } else if (worldTemp <= 0.0 || (worldTemp > 0.05 && worldTemp <= 0.25)) {
                        target = -55.0;  // Freezing (Level 1 Frozen)
                    } else if (worldTemp >= 50.0 || (worldTemp >= 1.5 && worldTemp <= 5.0)) {
                        target = 80.0;   // Hot
                    } else {
                        target = 0.0;    // Comfortable / habitable range
                    }
                }

                // Player body temperature moderates inventory food:
                // Normal body temp is ~37C. Hypothermia starts below 35C.
                if (!Double.isNaN(bodyTemp)) {
                    if (bodyTemp >= 40.0 || (bodyTemp > 0.8 && bodyTemp <= 2.0)) {
                        if (target < -55.0) {
                            target = -55.0; // Insulated
                        }
                    } else if (bodyTemp <= 28.0 || (bodyTemp >= -2.0 && bodyTemp <= -0.5)) {
                        target = Math.min(target, -100.0); // Severe hypothermia: Level 3 freezing
                    } else if (bodyTemp <= 32.0 || (bodyTemp > -0.5 && bodyTemp <= 0.0)) {
                        target = Math.min(target, -75.0);  // Moderate hypothermia: Level 2 freezing
                    } else if (bodyTemp <= 35.0 || (bodyTemp > 0.0 && bodyTemp <= 0.2)) {
                        target = Math.min(target, -55.0);  // Mild hypothermia: Level 1 freezing
                    }
                }

                // Holding heat provides warmth buffer against ambient cold
                if (holdingHeat && target < 0) {
                    target = Math.min(0.0, target + 50.0);
                }

                return target;
            }
        } catch (Throwable ignored) {}

        if (holdingHeat && posAmbient < 0) {
            posAmbient = Math.min(0.0, posAmbient + 50.0);
        }

        return posAmbient;
    }

    /**
     * Gets ambient temperature at a given location.
     * Below-freezing environments drive to graduated subzero temperatures.
     * Hot environments drive to 80.0 (Rotten).
     * Comfortable environments settle at 0.0 (Fresh).
     */
    public static double getAmbientTemperature(Level level, BlockPos pos) {
        if (level == null || pos == null) return 0.0;

        // 1. Nearby cold or hot blocks
        double blockTemp = checkNearbyColdBlocks(level, pos);
        if (!Double.isNaN(blockTemp)) {
            return blockTemp;
        }

        // 2. Serene Seasons seasonal temperature
        double sereneTemp = checkSereneSeasons(level, pos);
        if (!Double.isNaN(sereneTemp)) {
            return sereneTemp;
        }

        // 3. Cold Sweat WorldHelper
        double csTemp = checkColdSweatWorld(level, pos);
        if (!Double.isNaN(csTemp)) {
            return csTemp;
        }

        // 4. Vanilla Biome Fallback
        try {
            double biomeBase = level.getBiome(pos).value().getBaseTemperature();
            if (biomeBase <= -0.2) {
                return -100.0; // Ice Spikes, Frozen Peaks, Frozen Ocean (Level 3)
            } else if (biomeBase <= 0.05) {
                return -75.0;  // Snowy Plains, Snowy Slopes (Level 2)
            } else if (biomeBase <= 0.20) {
                return -65.0;  // Snowy Taiga (Level 1)
            } else if (biomeBase <= 0.35) {
                return -55.0;  // Taiga, Windswept Hills (Level 1)
            } else if (biomeBase >= 1.5) {
                return 80.0;   // Desert, Badlands
            }
        } catch (Throwable ignored) {}

        return 0.0;
    }

    /**
     * Checks if pos or directly adjacent blocks are icy, snowy, or hot.
     */
    private static double checkNearbyColdBlocks(Level level, BlockPos pos) {
        try {
            BlockPos[] checkPositions = new BlockPos[] {
                pos, pos.below(), pos.above(),
                pos.north(), pos.south(), pos.east(), pos.west()
            };
            double coldest = Double.NaN;
            for (BlockPos p : checkPositions) {
                BlockState state = level.getBlockState(p);
                // Heat sources take priority
                if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                    return 80.0;
                }
                if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
                    if (state.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT) &&
                        state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                        return 0.0; // Lit campfire keeps food fresh and prevents freezing
                    }
                }

                if (state.is(Blocks.POWDER_SNOW) || state.is(Blocks.BLUE_ICE)) {
                    coldest = -100.0;
                } else if (state.is(Blocks.PACKED_ICE)) {
                    if (Double.isNaN(coldest) || -75.0 < coldest) coldest = -75.0;
                } else if (state.is(Blocks.ICE)) {
                    if (Double.isNaN(coldest) || -50.0 < coldest) coldest = -50.0;
                } else if (state.is(Blocks.SNOW_BLOCK)) {
                    if (Double.isNaN(coldest) || -55.0 < coldest) coldest = -55.0;
                } else if (state.is(Blocks.SNOW)) {
                    // Snow layer chills item to Lightly Frozen (-55°C / Level 1)
                    if (p.equals(pos) || p.equals(pos.below())) {
                        if (Double.isNaN(coldest) || -55.0 < coldest) coldest = -55.0;
                    }
                }
            }
            if (!Double.isNaN(coldest)) {
                return coldest;
            }
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    /**
     * Checks Serene Seasons for seasonal freezing or temperature offset.
     */
    private static double checkSereneSeasons(Level level, BlockPos pos) {
        try {
            Class<?> seasonHooks = Class.forName("sereneseasons.season.SeasonHooks");

            // Method 1: coldEnoughToSnowSeasonal
            for (Method m : seasonHooks.getMethods()) {
                if (m.getName().startsWith("coldEnoughToSnow") && m.getParameterCount() >= 2) {
                    Object result = null;
                    if (m.getParameterCount() == 2) {
                        result = m.invoke(null, level, pos);
                    } else if (m.getParameterCount() == 3) {
                        result = m.invoke(null, level, level.getBiome(pos), pos);
                    }
                    if (Boolean.TRUE.equals(result)) {
                        return -50.0; // Snowy winter condition
                    }
                }
            }

            // Method 2: getBiomeTemperature
            for (Method m : seasonHooks.getMethods()) {
                if (m.getName().startsWith("getBiomeTemperature") && m.getParameterCount() >= 2) {
                    Object result = null;
                    if (m.getParameterCount() == 3) {
                        result = m.invoke(null, level, level.getBiome(pos), pos);
                    }
                    if (result instanceof Number num) {
                        float sTemp = num.floatValue();
                        if (sTemp <= 0.10f) {
                            return -100.0;
                        } else if (sTemp <= 0.25f) {
                            return -75.0;
                        } else if (sTemp <= 0.40f) {
                            return -55.0;
                        } else if (sTemp >= 1.3f) {
                            return 80.0;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    /**
     * Checks Cold Sweat's WorldHelper.getTemperatureAt for world temperature.
     */
    private static double checkColdSweatWorld(Level level, BlockPos pos) {
        try {
            Class<?> whClass = Class.forName("com.momosoftworks.coldsweat.util.world.WorldHelper");
            for (Method m : whClass.getMethods()) {
                if (m.getName().equals("getTemperatureAt") && m.getParameterCount() == 2) {
                    Object val = m.invoke(null, level, pos);
                    if (val instanceof Number num) {
                        double raw = num.doubleValue();
                        if (raw <= -20.0 || (raw >= -2.0 && raw <= -0.2)) {
                            return -100.0;
                        } else if (raw <= -10.0 || (raw > -0.2 && raw <= 0.05)) {
                            return -75.0;
                        } else if (raw <= 0.0 || (raw > 0.05 && raw <= 0.25)) {
                            return -55.0;
                        } else if (raw >= 50.0 || (raw >= 1.5 && raw <= 5.0)) {
                            return 80.0;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    /**
     * Gets current food temperature from the fiahi:food DataComponent or FIAHI capability.
     */
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

    /**
     * Sets food temperature on both the vanilla DataComponent and FIAHI capability.
     */
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

    /**
     * Returns a human-readable state description matching FIAHI tooltip levels.
     */
    public static String getTempStateDescription(int temp) {
        if (temp <= -100) return "Completely Frozen (Level 3)";
        if (temp <= -75) return "Mostly Frozen (Level 2)";
        if (temp <= -50) return "Lightly Frozen (Level 1)";
        if (temp >= 75) return "Completely Rotten";
        if (temp >= 50) return "Mostly Rotten";
        if (temp >= 25) return "Lightly Rotten";
        return "Fresh";
    }

    /**
     * Helper to execute the freeze command for a player.
     */
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

    /**
     * Reports comprehensive temperature status for the player and nearby containers to chat.
     */
    public static int executeCheckCommand(CommandSourceStack source, ServerPlayer player) {
        double ambient = getAmbientTemperatureForPlayer(player);
        BlockPos pos = player.blockPosition();
        String biomeKey = player.level().getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("unknown");
        float biomeBase = player.level().getBiome(pos).value().getBaseTemperature();

        source.sendSuccess(() -> Component.literal("§6=== [HypothermiaCore] Temperature Diagnostic ==="), false);
        source.sendSuccess(() -> Component.literal("§7Position: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " | Biome: §e" + biomeKey + " §7(base=" + biomeBase + ")"), false);
        source.sendSuccess(() -> Component.literal("§7Calculated Food Target Temp: §b" + String.format("%.1f", ambient) + " (" + getTempStateDescription((int) Math.round(ambient)) + ")"), false);

        // Count food items in inventory
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

        // Scan nearby containers within 5 blocks
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

    /**
     * Registers /freezefood, /freeze_food, and /freezestatus commands.
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("freezefood")
            .requires(source -> true)
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return executeFreezeCommand(ctx.getSource(), player, -100);
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
                    return executeFreezeCommand(ctx.getSource(), player, 0);
                })
            )
            .then(Commands.literal("frozen")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return executeFreezeCommand(ctx.getSource(), player, -100);
                })
            )
            .then(Commands.literal("rotten")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return executeFreezeCommand(ctx.getSource(), player, 100);
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

        // Alias /freeze_food
        dispatcher.register(Commands.literal("freeze_food")
            .requires(source -> true)
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return executeFreezeCommand(ctx.getSource(), player, -100);
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
