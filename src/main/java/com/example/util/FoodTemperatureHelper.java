package com.example.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Method;
import java.util.List;

public class FoodTemperatureHelper {
    private static final ResourceLocation FIAHI_FOOD_ID = ResourceLocation.fromNamespaceAndPath("fiahi", "food");
    private static Method coldSweatGetTempAt = null;
    private static boolean coldSweatChecked = false;
    private static Method getChunksMethod = null;
    private static boolean getChunksMethodChecked = false;
    private static int containerTickCounter = 0;

    /**
     * Ticks food items in a player's inventory based on ambient temperature.
     */
    public static void tickInventory(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;

        double ambientTemp = getAmbientTemperature(level, player.blockPosition());

        // If player is freezing in powder snow or has freeze ticks, food gets even colder
        if (player.getTicksFrozen() > 0) {
            ambientTemp = -100.0;
        }

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                tickFood(stack, ambientTemp);
            }
        }
    }

    /**
     * Ticks containers (chests, barrels, etc.) in loaded ticking chunks.
     * Called on ServerTickEvents.END_WORLD_TICK every 40 ticks (~2 seconds).
     */
    public static void onWorldTick(ServerLevel level) {
        containerTickCounter++;
        if (containerTickCounter < 40) return;
        containerTickCounter = 0;

        try {
            ServerChunkCache chunkCache = level.getChunkSource();
            if (!getChunksMethodChecked) {
                getChunksMethodChecked = true;
                try {
                    getChunksMethod = net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("getChunks");
                    getChunksMethod.setAccessible(true);
                } catch (Throwable ignored) {}
            }
            if (getChunksMethod == null) return;

            @SuppressWarnings("unchecked")
            Iterable<ChunkHolder> chunks = (Iterable<ChunkHolder>) getChunksMethod.invoke(chunkCache.chunkMap);
            for (ChunkHolder holder : chunks) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk == null || chunk.isEmpty()) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof Container container)) continue;

                    // Skip iceboxes and boilers (handled by Cold Sweat / FIAHI)
                    ResourceLocation beType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                    if (beType != null && (beType.getPath().contains("icebox") || beType.getPath().contains("boiler"))) {
                        continue;
                    }

                    double ambientTemp = getAmbientTemperature(level, be.getBlockPos());
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
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore any chunk iteration exceptions
        }
    }

    /**
     * Ticks a single food stack towards targetTemp.
     * Adjusts temperature smoothly by up to 4.0 degrees per interval.
     */
    public static void tickFood(ItemStack stack, double targetTemp) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) return;

        double currentTemp = getFoodTemperature(stack);
        double diff = targetTemp - currentTemp;

        // If very close to target, stop adjusting
        if (Math.abs(diff) < 0.5) return;

        // Apply up to 4.0 degrees change per check (2 seconds)
        // This gives noticeable, responsive freezing in 25-50 seconds!
        double step = Math.signum(diff) * Math.min(Math.abs(diff), 4.0);
        double newTemp = currentTemp + step;

        setFoodTemperature(stack, newTemp);
    }

    /**
     * Gets the ambient temperature at a given location.
     * Integrates with Cold Sweat's WorldHelper.getTemperatureAt, with fallback to biome base temp.
     * Converts Minecraft temperature units (MC units) to FIAHI food temperature (-100 to +100).
     */
    public static double getAmbientTemperature(Level level, BlockPos pos) {
        double rawTemp = Double.NaN;

        if (!coldSweatChecked) {
            coldSweatChecked = true;
            try {
                Class<?> whClass = Class.forName("com.momosoftworks.coldsweat.util.world.WorldHelper");
                coldSweatGetTempAt = whClass.getMethod("getTemperatureAt", Level.class, BlockPos.class);
            } catch (Throwable ignored) {}
        }

        if (coldSweatGetTempAt != null) {
            try {
                Object val = coldSweatGetTempAt.invoke(null, level, pos);
                if (val instanceof Number num) {
                    rawTemp = num.doubleValue();
                }
            } catch (Throwable ignored) {}
        }

        if (Double.isNaN(rawTemp)) {
            // Fallback: Vanilla biome temperature
            try {
                rawTemp = (double) level.getBiome(pos).value().getBaseTemperature();
            } catch (Throwable ignored) {
                rawTemp = 0.8;
            }
        }

        // Convert Minecraft temperature units (MC units) to FIAHI food temperature (-100 to +100)
        // In Minecraft & Cold Sweat:
        // <= 0.15 is freezing (Minecraft snow line threshold)
        // 0.8 is temperate (plains, forest)
        // >= 1.2 is hot (desert, badlands, nether)
        if (rawTemp <= 0.15) {
            // Cold environment:
            // rawTemp = 0.15 -> -50.0 (Level 1 Lightly Frozen)
            // rawTemp = 0.0  -> -80.0 (Level 2 Mostly Frozen)
            // rawTemp <= -0.1 -> -100.0 (Level 3 Completely Frozen)
            double coldTarget = -50.0 - ((0.15 - rawTemp) / 0.25) * 50.0;
            return Math.max(-100.0, coldTarget);
        } else if (rawTemp >= 1.2) {
            // Hot environment (causes food to spoil):
            double hotTarget = 50.0 + ((rawTemp - 1.2) / 0.8) * 50.0;
            return Math.min(100.0, hotTarget);
        } else {
            // Temperate / room temperature (fresh food, thaws if previously frozen)
            return 0.0;
        }
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
            source.sendSuccess(() -> Component.literal(
                "§b[HypothermiaCore] Gave 1x Cooked Beef with temperature " + temp + " (" + desc + ")!"
            ), false);
            return 1;
        }
    }

    /**
     * Registers /freezefood and /freeze_food commands.
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("freezefood")
            .requires(source -> true) // Allow any player / creative mode for easy testing
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                return executeFreezeCommand(ctx.getSource(), player, -100);
            })
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
