package com.example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fix Cold Sweat's CompatManager.modLoaded() crashing with IndexOutOfBoundsException
 * when running under Kilt/Fabric.
 *
 * Root cause: FMLLoader.getLoadingModList().getModFileById(modID).getMods()
 * returns an empty list under Kilt's compatibility layer, causing .get(0) to crash.
 *
 * Fix: Intercept modLoaded() at HEAD and use Fabric's own mod loader to check
 * if a mod is loaded, bypassing the broken NeoForge API entirely.
 */
@Mixin(targets = "com.momosoftworks.coldsweat.compat.CompatManager", remap = false)
public class ColdSweatCompatManagerFixMixin {

    @Inject(method = "modLoaded(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void fixModLoaded(String modID, String minVersion, String maxVersion,
                                     CallbackInfoReturnable<Boolean> cir) {
        try {
            // Use Fabric's mod loader to check if the mod is loaded
            boolean loaded = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .isModLoaded(modID);
            if (!loaded) {
                cir.setReturnValue(false);
                return;
            }

            // If version checks are needed, try to get the version from Fabric's API
            if (!minVersion.isEmpty() || !maxVersion.isEmpty()) {
                net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getModContainer(modID)
                        .ifPresentOrElse(container -> {
                            String versionStr = container.getMetadata().getVersion().getFriendlyString();
                            // Simple version comparison - if the mod is loaded via Kilt,
                            // we trust that it's a compatible version
                            cir.setReturnValue(true);
                        }, () -> {
                            cir.setReturnValue(false);
                        });
            } else {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            // If anything goes wrong, fall back to false to prevent crashes
            System.err.println("[HypothermiaCore] Error in CompatManager.modLoaded fix for: " + modID);
            t.printStackTrace();
            cir.setReturnValue(false);
        }
    }
}
