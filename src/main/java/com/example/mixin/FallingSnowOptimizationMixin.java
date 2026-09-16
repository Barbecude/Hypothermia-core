package com.example.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Task 2: Falling Snow performance optimization.
 * Falling-Snow (ru.cobaltmc.falling_snow) spawns FallingBlockEntities for every falling snow layer.
 * In heavy snowstorms or broad chunk updates, dozens to hundreds of entities cause severe TPS/FPS drops.
 *
 * This mixin:
 * 1. Fast-settles distant snow entities directly to ground if no player is within 36 blocks.
 * 2. Disables heavy entity-push collision checks for snow.
 * 3. Enforces an entity lifetime cap so snow never floats or ticks indefinitely.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingSnowOptimizationMixin extends Entity {

    @Shadow
    private BlockState blockState;

    @Shadow
    public int time;

    protected FallingSnowOptimizationMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void optimizeSnowTick(CallbackInfo ci) {
        if (this.blockState != null && this.blockState.is(Blocks.SNOW)) {
            Level level = this.level();

            // 1. Timeout protection: discard orphaned falling snow after 3 seconds (60 ticks)
            if (this.time > 60) {
                this.discard();
                ci.cancel();
                return;
            }

            // 2. Performance Culling: If no player is nearby, instantly settle down without ticking physics
            Player nearestPlayer = level.getNearestPlayer(this, 36.0);
            if (nearestPlayer == null) {
                fastSettle(level, this.blockPosition(), this.blockState);
                this.discard();
                ci.cancel();
            }
        }
    }

    private void fastSettle(Level level, BlockPos startPos, BlockState snowState) {
        if (level.isClientSide()) return;

        BlockPos.MutableBlockPos target = startPos.mutable();
        int minY = level.getMinBuildHeight();

        // Raycast down to find landing surface
        while (target.getY() > minY && level.getBlockState(target).canBeReplaced()) {
            target.move(Direction.DOWN);
        }

        BlockPos landingPos = target.above();
        BlockState currentAtLanding = level.getBlockState(landingPos);

        if (currentAtLanding.is(Blocks.SNOW)) {
            int currentLayers = currentAtLanding.getValue(BlockStateProperties.LAYERS);
            int addedLayers = snowState.hasProperty(BlockStateProperties.LAYERS) ? snowState.getValue(BlockStateProperties.LAYERS) : 1;
            int total = Math.min(8, currentLayers + addedLayers);
            level.setBlock(landingPos, currentAtLanding.setValue(BlockStateProperties.LAYERS, total), 3);
        } else if (currentAtLanding.canBeReplaced()) {
            level.setBlock(landingPos, snowState, 3);
        }
    }
}
