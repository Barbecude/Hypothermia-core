package com.example.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class HypothermiaMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        System.out.println("[HypothermiaCore] Loading HypothermiaMixinPlugin...");
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
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override
    public List<String> getMixins() { return null; }
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
