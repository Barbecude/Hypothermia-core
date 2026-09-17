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
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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

    private static Method getChunksMethod = null;
    private static boolean getChunksMethodChecked = false;
    private static int containerTickCounter = 0;

    /**
     * Ticks food items in a player's inventory based on ambient/player temperature.
     */
    public static void tickInventory(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel)) return;

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

        // Broadcast inventory changes immediately to client
        if (changed && player instanceof ServerPlayer sp) {
            sp.inventoryMenu.broadcastChanges();
            if (sp.containerMenu != null && sp.containerMenu != sp.inventoryMenu) {
                sp.containerMenu.broadcastChanges();
            }
        }
    }

    /**
     * Ticks containers (chests, barrels, etc.) in loaded ticking chunks.
     * Called on ServerTickEvents.END_WORLD_TICK every 20 ticks (~1 second).
     */
    public static void onWorldTick(ServerLevel level) {
        containerTickCounter++;
        if (containerTickCounter < 20) return;
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
        } catch (Throwable ignored) {}
    }

    /**
     * Ticks a single food stack towards targetTemp.
     * Adjusts temperature smoothly by up to 5.0 degrees per interval (1 second).
     */
    public static void tickFood(ItemStack stack, double targetTemp) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) return;

        double currentTemp = getFoodTemperature(stack);
        double diff = targetTemp - currentTemp;

        // If very close to target, stop adjusting
        if (Math.abs(diff) < 0.5) return;

        // Apply up to 5.0 degrees change per interval (1 second)
        // With tick rate 100 or fast ticks, reaches -25 in 5 ticks, -100 in 20 ticks!
        double step = Math.signum(diff) * Math.min(Math.abs(diff), 5.0);
        double newTemp = currentTemp + step;

        setFoodTemperature(stack, newTemp);
    }

    /**
     * Calculates ambient temperature for a player.
     * Checks powder snow ticks, Cold Sweat player traits (WORLD and BODY), Serene Seasons, nearby blocks, and biome.
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
                // Anything <= 0.42 MC units causes player hypothermia
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
                    // Map: 0.42 -> -25.0 (Lightly Frozen), 0.20 -> -65.0 (Mostly Frozen), <= 0.0 -> -100.0 (Completely Frozen)
                    double coldTarget = -25.0 - ((0.42 - effectiveCold) / 0.42) * 75.0;
                    return Math.max(-100.0, Math.min(-25.0, coldTarget));
                } else if (!Double.isNaN(worldTemp) && worldTemp >= 1.2) {
                    double hotTarget = 50.0 + ((worldTemp - 1.2) / 0.8) * 50.0;
                    return Math.min(100.0, hotTarget);
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fall back to position-based calculation (Serene Seasons, nearby ice/snow blocks, biome)
        return getAmbientTemperature(player.level(), player.blockPosition());
    }

    /**
     * Gets ambient temperature at a given location.
     * Prioritizes:
     * 1. Cold blocks (Powder Snow, Blue Ice, Packed Ice, Ice, Snow)
     * 2. Serene Seasons (coldEnoughToSnow / seasonal biome temperature)
     * 3. Cold Sweat WorldHelper (World temperature at block pos)
     * 4. Vanilla Biome base temperature
     */
    public static double getAmbientTemperature(Level level, BlockPos pos) {
        if (level == null || pos == null) return 0.0;

        // 1. Nearby cold blocks (Ice, Snow, Powder Snow)
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
            if (biomeBase <= 0.15) {
                return -100.0; // Snowy Plains, Ice Spikes, Frozen Peaks
            } else if (biomeBase <= 0.35) {
                return -50.0;  // Taiga, Grove, Windswept Hills
            } else if (biomeBase >= 1.5) {
                return 80.0;   // Desert, Badlands, Nether
            }
        } catch (Throwable ignored) {}

        return 0.0;
    }

    /**
     * Checks if pos or directly adjacent blocks are icy or snowy.
     */
    private static double checkNearbyColdBlocks(Level level, BlockPos pos) {
        try {
            BlockPos[] checkPositions = new BlockPos[] {
                pos, pos.below(), pos.above(),
                pos.north(), pos.south(), pos.east(), pos.west()
            };
            for (BlockPos p : checkPositions) {
                BlockState state = level.getBlockState(p);
                if (state.is(Blocks.POWDER_SNOW) || state.is(Blocks.BLUE_ICE)) {
                    return -100.0;
                }
                if (state.is(Blocks.PACKED_ICE)) {
                    return -80.0;
                }
                if (state.is(Blocks.ICE)) {
                    return -60.0;
                }
                if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                    return -50.0;
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

            // Method 1: coldEnoughToSnowSeasonal(Level/LevelReader, BlockPos)
            for (Method m : seasonHooks.getMethods()) {
                if (m.getName().equals("coldEnoughToSnowSeasonal") && m.getParameterCount() == 2) {
                    Boolean isCold = (Boolean) m.invoke(null, level, pos);
                    if (Boolean.TRUE.equals(isCold)) {
                        return -80.0; // Winter / freezing snow season!
                    }
                }
            }

            // Method 2: getBiomeTemperature(Level/LevelReader, Holder<Biome>, BlockPos)
            for (Method m : seasonHooks.getMethods()) {
                if (m.getName().equals("getBiomeTemperature") && m.getParameterCount() == 3) {
                    Object biomeHolder = level.getBiome(pos);
                    Number tempNum = (Number) m.invoke(null, level, biomeHolder, pos);
                    if (tempNum != null) {
                        float sTemp = tempNum.floatValue();
                        if (sTemp <= 0.15f) {
                            return -100.0;
                        } else if (sTemp <= 0.42f) {
                            double target = -25.0 - ((0.42 - sTemp) / 0.42) * 75.0;
                            return Math.max(-100.0, Math.min(-25.0, target));
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
                            double target = -25.0 - ((0.42 - raw) / 0.42) * 75.0;
                            return Math.max(-100.0, Math.min(-25.0, target));
                        } else if (raw >= 1.2) {
                            double hotTarget = 50.0 + ((raw - 1.2) / 0.8) * 50.0;
                            return Math.min(100.0, hotTarget);
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
     * Reports comprehensive temperature status for the player to chat.
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
        source.sendSuccess(() -> Component.literal("§7Food items in inventory: §a" + finalFoodCount + (foodItems.isEmpty() ? "" : " §8[" + String.join(", ", foodItems) + "]")), false);
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
