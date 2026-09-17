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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

/**
 * Allows snow to pile up to 5 blocks (40 snow layers total) during Winter in Serene Seasons.
 * Snow accumulates layer-by-layer up to 8 layers per block, then stacks upon the next block,
 * and seamlessly melts away block-by-block when Spring arrives.
 */
@Mixin(ServerLevel.class)
public abstract class SereneSeasonsWinterSnowMixin {

    @Unique
    private static Method hypothermia$getSeasonStateMethod = null;
    @Unique
    private static Method hypothermia$getSeasonMethod = null;
    @Unique
    private static boolean hypothermia$reflectionInitialized = false;

    @Redirect(
        method = "tickPrecipitation",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean redirectShouldSnowInWinter(Biome biome, LevelReader levelReader, BlockPos pos) {
        if (levelReader instanceof ServerLevel serverLevel && hypothermia$isWinter(serverLevel)) {
            if (biome.shouldSnow(serverLevel, pos)) {
                hypothermia$accumulateWinterSnow(serverLevel, pos, 5);
            }
            // Return false so vanilla's restricted 1-block/8-layer accumulation logic is bypassed
            return false;
        }

        return biome.shouldSnow(levelReader, pos);
    }

    @Unique
    private static boolean hypothermia$isWinter(ServerLevel level) {
        if (!hypothermia$reflectionInitialized) {
            hypothermia$reflectionInitialized = true;
            try {
                Class<?> helperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
                for (Method m : helperClass.getMethods()) {
                    if (m.getName().equals("getSeasonState") && m.getParameterCount() == 1) {
                        hypothermia$getSeasonStateMethod = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (hypothermia$getSeasonStateMethod != null) {
            try {
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
                            return "WINTER".equalsIgnoreCase(season.toString());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    @Unique
    private static void hypothermia$accumulateWinterSnow(ServerLevel level, BlockPos initialPos, int maxBlocks) {
        BlockPos.MutableBlockPos targetPos = initialPos.mutable();

        // 1. Move down if initialPos is high in the air and block below can be replaced/is air
        while (targetPos.getY() > level.getMinBuildHeight()
                && level.getBlockState(targetPos).isAir()
                && level.getBlockState(targetPos.below()).canBeReplaced()) {
            targetPos.move(Direction.DOWN);
        }

        // 2. Climb up if targetPos is already a full snow block or snow layer with 8 layers
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

        // 4. Perform snow accumulation up to maxBlocks
        if (targetState.is(Blocks.SNOW)) {
            int currentLayers = targetState.getValue(SnowLayerBlock.LAYERS);
            if (currentLayers < 8 && snowDepth <= maxBlocks) {
                BlockState newState = targetState.setValue(SnowLayerBlock.LAYERS, currentLayers + 1);
                Block.pushEntitiesUp(targetState, newState, level, targetPos);
                level.setBlockAndUpdate(targetPos, newState);
            }
        } else if (targetState.isAir() || targetState.canBeReplaced()) {
            if (snowDepth < maxBlocks) {
                if (level.getBrightness(LightLayer.BLOCK, targetPos) < 10) {
                    BlockState newState = Blocks.SNOW.defaultBlockState();
                    if (newState.canSurvive(level, targetPos)) {
                        level.setBlockAndUpdate(targetPos, newState);
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
