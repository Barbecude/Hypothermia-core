package com.example.mixin;

import com.illusivesoulworks.spectrelib.config.SpectreConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.illusivesoulworks.spectrelib.config.SpectreConfigSpec$ConfigValue", remap = false)
public abstract class SpectreConfigValueFixMixin<T> {
    @Shadow
    private SpectreConfigSpec spec;

    @Shadow
    private Object cachedValue;

    @Shadow
    public abstract T getDefault();

    @SuppressWarnings("unchecked")
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void hypothermia$safeGet(CallbackInfoReturnable<T> cir) {
        if (this.spec == null || !this.spec.isLoaded()) {
            if (this.cachedValue != null) {
                cir.setReturnValue((T) this.cachedValue);
            } else {
                cir.setReturnValue(this.getDefault());
            }
        }
    }
}
