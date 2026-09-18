package com.example.mixin;

import com.example.util.FoodTemperatureHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ServerLevel.tickChunk to reliably tick all containers (chests, barrels, etc.)
 * in loaded ticking chunks without fragile reflection.
 */
@Mixin(ServerLevel.class)
public class ContainerFoodTemperatureTickMixin {
    @Inject(method = "tickChunk", at = @At("TAIL"))
    private void hypothermia$tickChunkContainers(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        // Tick containers synchronously every 20 ticks (1 second) in lockstep with inventory
        if (level.getGameTime() % 5 != 0) return;

        FoodTemperatureHelper.tickChunkContainers(level, chunk);
    }
}
