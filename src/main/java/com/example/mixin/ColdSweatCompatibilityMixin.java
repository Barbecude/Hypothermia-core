package com.example.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Task 1: Cold Sweat compatibility under Kilt.
 * Safely hooks into ShearsDispenseItemBehavior using the actual Fabric/Vanilla descriptor:
 * tryShearLivingEntity(ServerLevel, BlockPos)
 * and reflectively invokes Cold Sweat's ShearableFurManager if present.
 */
@Mixin(ShearsDispenseItemBehavior.class)
public class ColdSweatCompatibilityMixin {

    private static Method shearMethod = null;
    private static boolean reflectionAttempted = false;

    @Inject(method = "tryShearLivingEntity", at = @At("TAIL"), cancellable = true)
    private static void tryShearColdSweatFur(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!reflectionAttempted) {
            reflectionAttempted = true;
            try {
                Class<?> managerClass = Class.forName("com.momosoftworks.coldsweat.common.capability.handler.ShearableFurManager");
                shearMethod = managerClass.getMethod("shear", LivingEntity.class, Player.class);
            } catch (Throwable ignored) {
            }
        }

        if (shearMethod != null) {
            try {
                boolean success = false;
                for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), EntitySelector.NO_SPECTATORS)) {
                    Object result = shearMethod.invoke(null, living, null);
                    if (Boolean.TRUE.equals(result)) {
                        success = true;
                    }
                }
                if (success) {
                    cir.setReturnValue(true);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
