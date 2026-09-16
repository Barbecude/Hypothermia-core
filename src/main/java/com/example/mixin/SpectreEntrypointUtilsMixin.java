package com.example.mixin;

import com.illusivesoulworks.spectrelib.EntrypointUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = EntrypointUtils.class, remap = false)
public abstract class SpectreEntrypointUtilsMixin {
    @Inject(method = "invokeEntrypoints", at = @At("HEAD"))
    private static <T> void hypothermia$alsoInvokeSpectrelibEntrypoint(String key, Class<T> type, Consumer<? super T> action, CallbackInfo ci) {
        if ("spectrelib-config".equals(key)) {
            for (EntrypointContainer<T> container : FabricLoader.getInstance().getEntrypointContainers("spectrelib", type)) {
                try {
                    action.accept(container.getEntrypoint());
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
