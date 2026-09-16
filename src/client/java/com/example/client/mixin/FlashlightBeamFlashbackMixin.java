package com.example.client.mixin;

import com.example.client.FlashlightBeamHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects into IRL Flashlight's FlashlightBeam.emit to emit beams
 * for all recorded entities during Flashback replay playback and video export.
 */
@Pseudo
@Mixin(targets = "com.irlights.flashlight.client.FlashlightBeam")
public class FlashlightBeamFlashbackMixin {

    @Inject(method = "emit", at = @At("HEAD"))
    private static void hypothermia$emitFlashbackBeams(float delta, CallbackInfo ci) {
        FlashlightBeamHelper.emitBeams(delta);
    }
}
