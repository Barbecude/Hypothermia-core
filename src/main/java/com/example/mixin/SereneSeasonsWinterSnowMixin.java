package com.example.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Allows snow to pile up to 5 blocks (40 snow layers total) during Winter in Serene Seasons.
 * Snow accumulates layer-by-layer up to 8 layers per block, stacks vertically onto subsequent blocks,
 * drifts naturally, and seamlessly melts away when Spring arrives.
 */
@Mixin(ServerLevel.class)
public abstract class SereneSeasonsWinterSnowMixin {

    @Unique
    private static Method hypothermia$getSeasonStateMethod = null;
    @Unique
    private static Method hypothermia$getSeasonMethod = null;
    @Unique
    private static Method hypothermia$coldEnoughMethod = null;
    @Unique
    private static boolean hypothermia$reflectionInitialized = false;
    @Unique
    private static long hypothermia$lastLogTime = 0L;
    @Unique
    private static boolean hypothermia$loggedWinterDetection = false;

    /**
     * Injects at HEAD of tickPrecipitation to autonomously handle winter snow accumulation
     * up to 5 full blocks (40 layers total), bypassing Serene Seasons' and Vanilla's 1-layer limitation.
     */
    @Inject(method = "tickPrecipitation", at = @At("HEAD"))
    private void onTickPrecipitationWinterSnow(BlockPos pos, CallbackInfo ci) {
        ServerLevel serverLevel = (ServerLevel) (Object) this;
        if (serverLevel.isRaining() && hypothermia$isWinter(serverLevel)) {
            BlockPos snowPos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
            if (hypothermia$canSnowAt(serverLevel, snowPos)) {
                hypothermia$spreadWinterSnow(serverLevel, snowPos, 5);
            }
        }
    }

    /**
     * Bypasses vanilla's restricted 1-block/8-layer accumulation logic during Winter
     * since winter accumulation is actively handled at HEAD with multi-block support.
     */
    @Redirect(
        method = "tickPrecipitation",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean redirectShouldSnowInWinter(Biome biome, LevelReader levelReader, BlockPos pos) {
        if (levelReader instanceof ServerLevel serverLevel && hypothermia$isWinter(serverLevel)) {
            // Return false so vanilla's restricted 1-block accumulation is bypassed
            return false;
        }

        return biome.shouldSnow(levelReader, pos);
    }

    @Unique
    private static boolean hypothermia$isWinter(ServerLevel level) {
        // Method 1: SeasonHelper.getSeasonState(level)
        try {
            if (!hypothermia$reflectionInitialized) {
                hypothermia$reflectionInitialized = true;
                Class<?> helperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
                for (Method m : helperClass.getDeclaredMethods()) {
                    if (m.getName().equals("getSeasonState") && m.getParameterCount() == 1) {
                        hypothermia$getSeasonStateMethod = m;
                        break;
                    }
                }
            }

            if (hypothermia$getSeasonStateMethod != null) {
                Object seasonState = hypothermia$getSeasonStateMethod.invoke(null, level);
                if (seasonState != null) {
                    if (hypothermia$getSeasonMethod == null) {
                        for (Method m : seasonState.getClass().getMethods()) {
                            if (m.getName().equals("getSeason") && m.getParameterCount() == 0) {
                                hypothermia$getSeasonMethod = m;
                                break;
                            }
                        }
                    }
                    if (hypothermia$getSeasonMethod != null) {
                        Object season = hypothermia$getSeasonMethod.invoke(seasonState);
                        if (season != null) {
                            boolean isW = "WINTER".equalsIgnoreCase(season.toString());
                            if (!hypothermia$loggedWinterDetection) {
                                hypothermia$loggedWinterDetection = true;
                                System.out.println("[HypothermiaCore] Serene Seasons detected via SeasonHelper. Current Season: " + season + " (isWinter=" + isW + ")");
                            }
                            return isW;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Method 2: SeasonHandler.getSeasonSavedData(level) -> SeasonTime
        try {
            Class<?> handlerClass = Class.forName("sereneseasons.season.SeasonHandler");
            for (Method m : handlerClass.getDeclaredMethods()) {
                if (m.getName().equals("getSeasonSavedData") && m.getParameterCount() == 1) {
                    Object savedData = m.invoke(null, level);
                    if (savedData != null) {
                        Field f = savedData.getClass().getField("seasonCycleTicks");
                        int ticks = f.getInt(savedData);
                        Class<?> stClass = Class.forName("sereneseasons.season.SeasonTime");
                        Constructor<?> ctor = stClass.getConstructor(int.class);
                        Object st = ctor.newInstance(ticks);
                        Method getSeason = stClass.getMethod("getSeason");
                        Object season = getSeason.invoke(st);
                        if (season != null) {
                            boolean isW = "WINTER".equalsIgnoreCase(season.toString());
                            if (!hypothermia$loggedWinterDetection) {
                                hypothermia$loggedWinterDetection = true;
                                System.out.println("[HypothermiaCore] Serene Seasons detected via SeasonHandler. Current Season: " + season + " (isWinter=" + isW + ")");
                            }
                            return isW;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    @Unique
    private static boolean hypothermia$canSnowAt(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }

        // Check Serene Seasons seasonal temperature
        try {
            if (hypothermia$coldEnoughMethod == null) {
                Class<?> seasonHooks = Class.forName("sereneseasons.season.SeasonHooks");
                for (Method m : seasonHooks.getDeclaredMethods()) {
                    if (m.getName().equals("coldEnoughToSnowSeasonal") && m.getParameterCount() == 2) {
                        hypothermia$coldEnoughMethod = m;
                        break;
                    }
                }
            }
            if (hypothermia$coldEnoughMethod != null) {
                Boolean cold = (Boolean) hypothermia$coldEnoughMethod.invoke(null, level, pos);
                if (cold != null) {
                    return cold;
                }
            }
        } catch (Throwable ignored) {
        }

        // Vanilla fallback
        return level.getBiome(pos).value().coldEnoughToSnow(pos);
    }

    @Unique
    private static void hypothermia$spreadWinterSnow(ServerLevel level, BlockPos centerPos, int maxBlocks) {
        hypothermia$accumulateWinterSnow(level, centerPos, maxBlocks);

        // Spread snow to 2 random neighboring columns to create natural drifts and active winter blizzards
        for (int i = 0; i < 2; i++) {
            int dx = level.random.nextInt(5) - 2; // -2 to +2
            int dz = level.random.nextInt(5) - 2;
            if (dx == 0 && dz == 0) continue;
            BlockPos offsetPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, centerPos.offset(dx, 0, dz));
            if (hypothermia$canSnowAt(level, offsetPos)) {
                hypothermia$accumulateWinterSnow(level, offsetPos, maxBlocks);
            }
        }
    }

    @Unique
    private static void hypothermia$accumulateWinterSnow(ServerLevel level, BlockPos initialPos, int maxBlocks) {
        BlockPos.MutableBlockPos targetPos = initialPos.mutable();

        // 1. If target is air/replaceable and block below is a snow layer with < 8 layers, target should be that snow layer below
        BlockState currentState = level.getBlockState(targetPos);
        BlockState belowState = level.getBlockState(targetPos.below());
        if ((currentState.isAir() || currentState.canBeReplaced()) && belowState.is(Blocks.SNOW)) {
            if (belowState.getValue(SnowLayerBlock.LAYERS) < 8) {
                targetPos.move(Direction.DOWN);
            }
        }

        // 2. Climb up if targetPos is full snow (snow block or 8-layer snow)
        while (targetPos.getY() < level.getMaxBuildHeight() - 1 && hypothermia$isFullSnow(level.getBlockState(targetPos))) {
            targetPos.move(Direction.UP);
        }

        // 3. Count snow depth below targetPos (including targetPos if it's already snow)
        BlockState targetState = level.getBlockState(targetPos);
        int snowDepth = 0;
        if (hypothermia$isSnow(targetState)) {
            snowDepth++;
        }

        BlockPos.MutableBlockPos checkDown = targetPos.mutable().move(Direction.DOWN);
        while (checkDown.getY() >= level.getMinBuildHeight() && hypothermia$isSnow(level.getBlockState(checkDown))) {
            snowDepth++;
            checkDown.move(Direction.DOWN);
        }

        // 4. Perform snow accumulation up to maxBlocks (5 blocks = 40 snow layers total)
        if (targetState.is(Blocks.SNOW)) {
            int currentLayers = targetState.getValue(SnowLayerBlock.LAYERS);
            if (currentLayers < 8 && snowDepth <= maxBlocks) {
                BlockState newState = targetState.setValue(SnowLayerBlock.LAYERS, currentLayers + 1);
                Block.pushEntitiesUp(targetState, newState, level, targetPos);
                level.setBlockAndUpdate(targetPos, newState);

                long now = System.currentTimeMillis();
                if (now - hypothermia$lastLogTime > 5000) {
                    hypothermia$lastLogTime = now;
                    System.out.println("[HypothermiaCore] Winter snow layer added at " + targetPos.toShortString()
                        + " (depth: block " + snowDepth + "/" + maxBlocks + ", layers: " + (currentLayers + 1) + "/8)");
                }
            }
        } else if (targetState.isAir() || targetState.canBeReplaced()) {
            if (snowDepth < maxBlocks) {
                if (level.getBrightness(LightLayer.BLOCK, targetPos) < 10) {
                    BlockState newState = Blocks.SNOW.defaultBlockState();
                    if (newState.canSurvive(level, targetPos)) {
                        Block.pushEntitiesUp(targetState, newState, level, targetPos);
                        level.setBlockAndUpdate(targetPos, newState);

                        long now = System.currentTimeMillis();
                        if (now - hypothermia$lastLogTime > 5000) {
                            hypothermia$lastLogTime = now;
                            System.out.println("[HypothermiaCore] Winter snow block started at " + targetPos.toShortString()
                                + " (depth: block " + (snowDepth + 1) + "/" + maxBlocks + ", layer 1/8)");
                        }
                    }
                }
            }
        }
    }

    @Unique
    private static boolean hypothermia$isSnow(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    @Unique
    private static boolean hypothermia$isFullSnow(BlockState state) {
        if (state.is(Blocks.SNOW_BLOCK)) {
            return true;
        }
        if (state.is(Blocks.SNOW)) {
            return state.getValue(SnowLayerBlock.LAYERS) == 8;
        }
        return false;
    }
}
