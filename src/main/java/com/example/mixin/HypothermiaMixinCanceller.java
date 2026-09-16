package com.example.mixin;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import java.util.List;

public class HypothermiaMixinCanceller implements MixinCanceller {
    public HypothermiaMixinCanceller() {
        System.out.println("[HypothermiaCore] Canceller instantiated!");
    }
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName == null) {
            return false;
        }

        // Task 1: Cold Sweat's MixinShearsDispenseBehavior invalid injection descriptor on Fabric/Vanilla
        if (mixinClassName.contains("MixinShearsDispenseBehavior")
                || (targetClassNames != null && (targetClassNames.contains("net.minecraft.class_5168")
                || targetClassNames.contains("net.minecraft.core.dispenser.ShearsDispenseItemBehavior"))
                && (mixinClassName.contains("cold_sweat") || mixinClassName.contains("coldsweat")))) {
            System.out.println("[HypothermiaCore] MixinSquared cancelled incompatible mixin: " + mixinClassName);
            return true;
        }

        // Task 2: Cold Sweat's MixinRegistration LVT incompatibility on Fabric/Kilt
        // loadRegistryContents expects 3 locals (Decoder, Reader, JsonElement) but only 2 exist in intermediary
        if (mixinClassName.contains("MixinRegistration")
                && (mixinClassName.contains("cold_sweat") || mixinClassName.contains("coldsweat"))) {
            System.out.println("[HypothermiaCore] MixinSquared cancelled incompatible mixin: " + mixinClassName);
            return true;
        }

        // Task 3: Spartan Weaponry's AbstractSkeletonMixin
        if (mixinClassName.contains("AbstractSkeletonMixin") && (mixinClassName.toLowerCase().contains("spartan") || mixinClassName.contains("AbstractSkeletonMixin"))) {
            System.out.println("[HypothermiaCore] MixinSquared cancelled incompatible mixin: " + mixinClassName);
            return true;
        }

        // Task 4: Diet's DietMixinServerPlayerGameMode (targets removed BlockState.use method in 1.21.1)
        if (mixinClassName.contains("DietMixinServerPlayerGameMode")
                || (targetClassNames != null && (targetClassNames.contains("net.minecraft.class_3225")
                || targetClassNames.contains("net.minecraft.server.level.ServerPlayerGameMode"))
                && (mixinClassName.contains("diet") || mixinClassName.contains("Diet")))) {
            System.out.println("[HypothermiaCore] MixinSquared cancelled incompatible mixin: " + mixinClassName);
            return true;
        }

        return false;
    }
}
