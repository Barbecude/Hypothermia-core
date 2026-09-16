package com.example.mixin;

import com.mojang.datafixers.util.Either;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes crash when Cold Sweat parses entity requirements from uninstalled optional mods.
 * 
 * Cold Sweat's EntityRequirement codec throws IllegalArgumentException when an entity type
 * is not found in BuiltInRegistries.ENTITY_TYPE, crashing registry/datapack loading.
 * 
 * This mixin intercepts lambda$static$0 and safely returns null as an inert placeholder.
 */
@Pseudo
@Mixin(targets = "com.momosoftworks.coldsweat.data.codec.requirement.EntityRequirement", remap = false)
public class ColdSweatEntityRequirementFixMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "lambda$static$0", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void onDecodeEntity(Either either, CallbackInfoReturnable<Either> cir) {
        if (either != null && either.left().isPresent()) {
            cir.setReturnValue((Either) either.left().get());
            return;
        }
        if (either != null && either.right().isPresent()) {
            String str = (String) either.right().get();
            if ("*".equals(str)) {
                return; // Let original handler map to WILDCARD_ENTITY
            }
            System.out.println("[HypothermiaCore] Missing entity requirement: " + str + " (safely substituting null)");
            cir.setReturnValue(Either.right(null));
        }
    }
}
