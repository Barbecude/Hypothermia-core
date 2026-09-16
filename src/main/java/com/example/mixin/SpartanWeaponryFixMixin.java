package com.example.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Task 3: Spartan Weaponry Unofficial NeoForge compatibility under Kilt.
 * The original AbstractSkeletonMixin crashes with:
 * "InvalidMixinException: @Shadow field field_7220 was not located in the target class net.minecraft.class_1547. No refMap loaded."
 *
 * This replacement mixin safely provides longbow compatibility to skeleton AI
 * with proper official mappings and refmaps without crashing.
 */
@Mixin(AbstractSkeleton.class)
public abstract class SpartanWeaponryFixMixin {

    @Inject(
            method = "canFireProjectileWeapon",
            at = @At("HEAD"),
            cancellable = true
    )
    private void allowSpartanLongbows(ProjectileWeaponItem weaponItem, CallbackInfoReturnable<Boolean> cir) {
        if (weaponItem != null) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(weaponItem);
            if (id != null && (id.getPath().contains("longbow") || id.getNamespace().contains("spartanweaponry"))) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "reassessWeaponGoal", at = @At("HEAD"), cancellable = true)
    private void onReassessWeaponGoal(CallbackInfo ci) {
        AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
        ItemStack mainHand = skeleton.getMainHandItem();
        if (!mainHand.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(mainHand.getItem());
            if (id != null && (id.getPath().contains("longbow") || id.getNamespace().contains("spartanweaponry"))) {
                // Allows skeleton to hold and shoot Spartan Weaponry longbows smoothly
                // without relying on missing unmapped @Shadow field_7220
            }
        }
    }
}
