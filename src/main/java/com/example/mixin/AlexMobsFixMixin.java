package com.example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Task 4: Alex's Mobs (Continued) 2.1.13 compatibility under Kilt.
 * Fixes ClassCastException:
 * "AMItemRenderProperties cannot be cast to net.neoforged.neoforge.client.extensions.common.IClientItemExtensions"
 * at com.github.alexthe666.alexsmobs.item.ItemCustomRender.initializeClient(ItemCustomRender.java:28).
 *
 * Intercepts initializeClient on ItemCustomRender and safely cancels it, preventing the illegal cast
 * on the Kilt NeoForge-shim layer while allowing creative tabs and game initialization to succeed.
 */
@Pseudo
@Mixin(targets = {
    "com.github.alexthe666.alexsmobs.item.BlockItemAMRender",
    "com.github.alexthe666.alexsmobs.item.ItemCustomRender",
    "com.github.alexthe666.alexsmobs.item.ItemTabIcon",
    "com.github.alexthe666.alexsmobs.item.ItemModArmor",
    "com.github.alexthe666.alexsmobs.item.ItemFalconryGlove",
    "com.github.alexthe666.alexsmobs.item.ItemFancyRender",
    "com.github.alexthe666.alexsmobs.item.ItemMysteriousWorm",
    "com.github.alexthe666.alexsmobs.item.ItemShatteredDimensionalCarver",
    "com.github.alexthe666.alexsmobs.item.ItemShieldOfTheDeep",
    "com.github.alexthe666.alexsmobs.item.ItemSkelewagSword",
    "com.github.alexthe666.alexsmobs.item.ItemStinkRay",
    "com.github.alexthe666.alexsmobs.item.ItemTarantulaHawkElytra",
    "com.github.alexthe666.alexsmobs.item.ItemVineLasso"
}, remap = false)
public class AlexMobsFixMixin {

    @Inject(method = "initializeClient", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void onInitializeClient(CallbackInfo ci) {
        // Suppress the incompatible NeoForge IClientItemExtensions cast on Kilt loader
        ci.cancel();
    }
}
