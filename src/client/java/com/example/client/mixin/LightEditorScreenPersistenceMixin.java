package com.example.client.mixin;

import com.example.client.IrlRedactorConfigPersistence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists IRL Redactor settings when the editor is opened or closed.
 *
 * No periodic saving is performed while the editor is rendering.
 */
@Pseudo
@Mixin(targets = "org.qualet.irlredactor.editor.LightEditorScreen")
public class LightEditorScreenPersistenceMixin {

    /**
     * Load settings when the editor becomes visible.
     * Save settings when the editor becomes hidden.
     */
    @Inject(
            method = "setVisible",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void hypothermia$onSetVisible(
            boolean visible,
            CallbackInfo ci
    ) {
        if (visible) {
            IrlRedactorConfigPersistence.loadConfig();
        } else {
            IrlRedactorConfigPersistence.saveAll();
        }
    }

    /**
     * Also save when the editor is explicitly closed.
     *
     * This is kept as a fallback for versions where close() is used
     * without setVisible(false).
     */
    @Inject(
            method = {"close", "method_25432"},
            at = @At("HEAD"),
            require = 0
    )
    private void hypothermia$onClose(CallbackInfo ci) {
        IrlRedactorConfigPersistence.saveAll();
    }
}