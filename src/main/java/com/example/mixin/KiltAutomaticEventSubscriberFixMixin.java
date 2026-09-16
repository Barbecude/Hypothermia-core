package com.example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.lang.reflect.Method;

/**
 * Protects Kilt's AutomaticEventSubscriber against LinkageError / NoClassDefFoundError.
 * 
 * Kilt's AutomaticEventSubscriber scans all @EventBusSubscriber classes and calls
 * Class.getDeclaredMethods() within a try-catch that only catches java.lang.Exception,
 * not java.lang.LinkageError / NoClassDefFoundError.
 * 
 * This mixin safely intercepts getDeclaredMethods() to return an empty array if any
 * LinkageError is encountered due to missing optional mod classes.
 */
@Mixin(targets = "net.neoforged.fml.javafmlmod.AutomaticEventSubscriber", remap = false)
public class KiltAutomaticEventSubscriberFixMixin {

    @Redirect(
        method = "lambda$inject$4",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Class;getDeclaredMethods()[Ljava/lang/reflect/Method;"
        ),
        remap = false,
        require = 0
    )
    private static Method[] safeGetDeclaredMethods(Class<?> clazz) {
        try {
            return clazz.getDeclaredMethods();
        } catch (Throwable t) {
            System.err.println("[HypothermiaCore] Safely handled reflection error on @EventBusSubscriber class "
                + clazz.getName() + ": " + t);
            return new Method[0];
        }
    }
}
