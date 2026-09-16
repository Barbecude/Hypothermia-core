package com.example.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes crash when Cold Sweat parses item requirements from uninstalled optional mods
 * (e.g. irons_spellbooks:frostward_ring, create:netherite_backtank).
 * 
 * Cold Sweat's ItemRequirement codec throws IllegalArgumentException when an item is
 * not found in BuiltInRegistries.ITEM, crashing registry/datapack loading during world
 * creation.
 * 
 * This mixin intercepts lambda$static$0 and safely returns Items.AIR as an inert placeholder.
 * Cold Sweat's ConfigLoadingHandler subsequently filters the entry out using areRequiredModsLoaded().
 */
@Pseudo
@Mixin(targets = "com.momosoftworks.coldsweat.data.codec.requirement.ItemRequirement", remap = false)
public class ColdSweatItemRequirementFixMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "lambda$static$0", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void onDecodeItem(Either either, CallbackInfoReturnable<Either> cir) {
        if (either != null && either.left().isPresent()) {
            cir.setReturnValue((Either) either.left().get());
            return;
        }
        if (either != null && either.right().isPresent()) {
            String str = (String) either.right().get();
            if ("*".equals(str)) {
                return; // Let original handler map to WILDCARD_ITEM
            }
            System.out.println("[HypothermiaCore] Missing item requirement: " + str + " (safely substituting placeholder item)");
            cir.setReturnValue(Either.right(Items.AIR));
        }
    }
}
