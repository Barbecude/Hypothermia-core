package com.example.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.illusivesoulworks.diet.client.screen.DietScreen")
public abstract class DietScreenFixMixin extends Screen {

    protected DietScreenFixMixin() {
        super(null);
    }

    @Inject(method = "method_25394", at = @At("TAIL"), remap = false, require = 0)
    private void hypothermia$resetRenderStateAfterContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
    }
}
