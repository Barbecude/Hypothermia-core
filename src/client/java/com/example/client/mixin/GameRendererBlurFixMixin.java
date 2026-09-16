package com.example.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels vanilla's post-processing blur effect when Iris shaderpacks are active.
 *
 * In Minecraft 1.21+, the vanilla post-processing blur pass conflicts with Iris's
 * frame/composite buffer pipeline, causing menus (Pause Menu, Diet GUI, Options, etc.)
 * to render invisibly with only the blurred 3D world visible.
 */
@Mixin(GameRenderer.class)
public class GameRendererBlurFixMixin {

    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void hypothermia$cancelBlurWhenShadersInUse(float partialTick, CallbackInfo ci) {
        if (isShaderPackInUse()) {
            ci.cancel();
        }
    }

    private static boolean isShaderPackInUse() {
        try {
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                return false;
            }
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = irisApiClass.getMethod("getInstance").invoke(null);
            if (api != null) {
                return (boolean) irisApiClass.getMethod("isShaderPackInUse").invoke(api);
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
