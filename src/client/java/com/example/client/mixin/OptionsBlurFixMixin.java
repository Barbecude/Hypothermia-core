package com.example.client.mixin;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Returns 0 for menu background blurriness when an Iris shaderpack is in use.
 * This ensures vanilla's blur pass is skipped cleanly at option resolution time.
 */
@Mixin(Options.class)
public class OptionsBlurFixMixin {

    @Inject(method = "getMenuBackgroundBlurriness", at = @At("HEAD"), cancellable = true)
    private void hypothermia$overrideMenuBlurWhenShadersInUse(CallbackInfoReturnable<Integer> cir) {
        try {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object api = irisApiClass.getMethod("getInstance").invoke(null);
                if (api != null && (boolean) irisApiClass.getMethod("isShaderPackInUse").invoke(api)) {
                    cir.setReturnValue(0);
                }
            }
        } catch (Throwable ignored) {}
    }
}
