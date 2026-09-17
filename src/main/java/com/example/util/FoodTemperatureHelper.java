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
            f.setInt(null, 999999);
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

        // Broadcast inventory changes immediately to client
        if (changed && player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * Ticks all containers (chests, barrels, shulker boxes, etc.) in a ticking chunk.
     * Single source of truth for containers: ensures identical speed whether open or closed.
     */
    public static void tickChunkContainers(ServerLevel level, LevelChunk chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        disableFiahiBrokenTick();

        for (BlockEntity be : chunk.getBlockEntities().values()) {
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

            // Check if container contains ice or cooling items (acts as a freezer)
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    if (s.is(Items.BLUE_ICE) || s.is(Items.PACKED_ICE) || s.is(Items.ICE) ||
                        s.is(Items.SNOW_BLOCK) || s.is(Items.SNOWBALL)) {
                        ambientTemp = -100.0;
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
        double baseStep = 5.0 * speedMultiplier;
        double step = Math.signum(diff) * Math.min(Math.abs(diff), baseStep);
        double newTemp = currentTemp + step;

        setFoodTemperature(stack, newTemp);
    }

    /**
     * Calculates ambient temperature for a player.
     * Freezing conditions drive directly to -100.0 (Completely Frozen).
     */
    public static double getAmbientTemperatureForPlayer(Player player) {
        if (player == null) return 0.0;

        // 1. Powder snow / vanilla freeze ticks
        if (player.getTicksFrozen() > 0) {
            return -100.0;
        }

        // 2. Direct Cold Sweat player traits
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

                // In Cold Sweat: Habitable is 0.40 to 1.51 MC units
                // Anything <= 0.42 MC units causes player hypothermia (Freezing!)
                double effectiveCold = Double.NaN;
                if (!Double.isNaN(worldTemp) && worldTemp <= 0.42) {
                    effectiveCold = worldTemp;
                }
                if (!Double.isNaN(bodyTemp) && bodyTemp <= 0.45) {
                    if (Double.isNaN(effectiveCold) || bodyTemp < effectiveCold) {
                        effectiveCold = bodyTemp;
                    }
                }

                if (!Double.isNaN(effectiveCold)) {
                    // Freezing conditions drive to Completely Frozen (-100.0)
                    return -100.0;
                } else if (!Double.isNaN(worldTemp) && worldTemp >= 1.2) {
                    return 100.0;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fall back to position-based calculation (Serene Seasons, nearby ice/snow blocks, biome)
        return getAmbientTemperature(player.level(), player.blockPosition());
    }

    /**
     * Gets ambient temperature at a given location.
     * Below-freezing environments drive to -100.0 (Completely Frozen).
     * Hot environments drive to 100.0 (Completely Rotten).
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
            if (biomeBase <= 0.35) {
                return -100.0; // Cold biomes drive to completely frozen
            } else if (biomeBase >= 1.5) {
                return 100.0;  // Hot biomes drive to rotten
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
            for (BlockPos p : checkPositions) {
                BlockState state = level.getBlockState(p);
                if (state.is(Blocks.POWDER_SNOW) || state.is(Blocks.BLUE_ICE) ||
                    state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE) ||
                    state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                    return -100.0;
                }
                if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) ||
                    state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE) ||
                    state.is(Blocks.SOUL_CAMPFIRE)) {
                    return 100.0;
                }
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
                        return -100.0; // Winter freezing!
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
                        if (sTemp <= 0.42f) {
                            return -100.0; // Cold enough to freeze
                        } else if (sTemp >= 1.2f) {
                            return 100.0;
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
                        if (raw <= 0.42) {
                            return -100.0; // Freezing world temp drives to Completely Frozen
                        } else if (raw >= 1.2) {
                            return 100.0;  // Hot world temp drives to Rotten
                        } else {
                            return 0.0;    // Comfortable / room temperature
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
                    Method updateTag = foodObj.getClass().getMethod("updateFoodTag");
                    setTemp.invoke(foodObj, (double) intTemp);
                    updateTag.invoke(foodObj);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Returns a human-readable state description matching FIAHI tooltip levels.
     */
    public static String getTempStateDescription(int temp) {
        if (temp <= -75) return "Completely Frozen";
        if (temp <= -50) return "Mostly Frozen";
        if (temp <= -25) return "Lightly Frozen";
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
