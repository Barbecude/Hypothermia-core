package com.example.client.mixin;

import com.example.client.IrlRedactorConfigPersistence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to persist IRL Redactor settings when the editor screen is opened,
 * closed, or actively tweaked.
 */
@Pseudo
@Mixin(targets = "org.qualet.irlredactor.editor.LightEditorScreen")
public class LightEditorScreenPersistenceMixin {

    @Inject(method = "setVisible", at = @At("HEAD"), remap = false, require = 0)
    private static void hypothermia$onSetVisible(boolean visible, CallbackInfo ci) {
        if (!visible) {
            IrlRedactorConfigPersistence.saveAll();
        } else {
            IrlRedactorConfigPersistence.loadConfig();
        }
    }

    @Inject(method = {"close", "method_25432"}, at = @At("HEAD"), require = 0)
    private void hypothermia$onClose(CallbackInfo ci) {
        IrlRedactorConfigPersistence.saveAll();
    }

    @Inject(method = "renderActiveOverlay", at = @At("HEAD"), remap = false, require = 0)
    private static void hypothermia$onRenderActiveOverlay(CallbackInfo ci) {
        IrlRedactorConfigPersistence.tickPeriodicSave();
    }
}
