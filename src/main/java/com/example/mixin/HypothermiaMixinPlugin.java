package com.example.mixin;

import com.bawnorton.mixinsquared.MixinSquaredBootstrap;
import com.bawnorton.mixinsquared.platform.fabric.MixinSquaredApiImplLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class HypothermiaMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        System.out.println("[HypothermiaCore] Loading HypothermiaMixinPlugin for " + mixinPackage + "...");
        try {
            MixinSquaredBootstrap.init();
            MixinSquaredApiImplLoader.load();
            System.out.println("[HypothermiaCore] MixinSquaredBootstrap initialized!");
        } catch (Throwable t) {
            System.err.println("[HypothermiaCore] Failed to initialize MixinSquared: " + t.getMessage());
            t.printStackTrace();
        }

        try {
            Class<?> registrar = Class.forName("com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar");
            java.lang.reflect.Method registerMethod = registrar.getMethod("register", Class.forName("com.bawnorton.mixinsquared.api.MixinCanceller"));
            registerMethod.invoke(null, new HypothermiaMixinCanceller());
            System.out.println("[HypothermiaCore] Registered HypothermiaMixinCanceller reflectively!");
        } catch (Throwable t) {
            System.err.println("[HypothermiaCore] Failed to register MixinCanceller manually.");
            t.printStackTrace();
        }
    }

    @Override
    public String getRefMapperConfig() { return null; }
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.contains("FlashlightBeamFlashbackMixin")) {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("irlights_flashlight");
        }
        if (mixinClassName != null && mixinClassName.contains("LightEditorScreenPersistenceMixin")) {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("irl-redactor");
        }
        if (mixinClassName != null && mixinClassName.contains("FoodTemperatureTickMixin")) {
            return true;
        }
        if (mixinClassName != null && mixinClassName.contains("SereneSeasonsWinterSnowMixin")) {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sereneseasons");
        }
        return true;
    }
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override
    public List<String> getMixins() {
        try {
            MixinSquaredBootstrap.reOrderExtensions();
        } catch (Throwable ignored) {}
        return null;
    }
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
