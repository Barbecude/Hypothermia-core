package com.example.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ItemRenderer.class, priority = 2000)
public abstract class ItemRendererFoodModelMixin {
    @ModifyVariable(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BakedModel hypothermia$resolveModelOverrides(BakedModel model, ItemStack stack, ItemDisplayContext displayContext) {
        if (model != null && stack != null && !stack.isEmpty()) {
            ItemOverrides overrides = model.getOverrides();
            if (overrides != null && overrides != ItemOverrides.EMPTY) {
                Minecraft mc = Minecraft.getInstance();
                ClientLevel level = mc.level;
                BakedModel resolved = overrides.resolve(model, stack, level, mc.player, 0);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return model;
    }
}
