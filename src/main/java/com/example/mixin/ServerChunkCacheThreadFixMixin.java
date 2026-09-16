package com.example.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(value = ServerChunkCache.class, priority = 500)
public abstract class ServerChunkCacheThreadFixMixin {

    @Shadow
    @Final
    @Mutable
    public Thread mainThread;

    @Inject(method = "getChunk", at = @At("HEAD"))
    private void hypothermia$syncMainThreadOnGetChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        Thread current = Thread.currentThread();
        if (this.mainThread != current) {
            String name = current.getName();
            if ("Server thread".equals(name) || name.contains("Server")) {
                this.mainThread = current;
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void hypothermia$syncMainThreadOnTick(BooleanSupplier hasTimeLeft, boolean clearCache, CallbackInfo ci) {
        Thread current = Thread.currentThread();
        if (this.mainThread != current) {
            this.mainThread = current;
        }
    }
}
