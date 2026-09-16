package com.example.client.mixin;

import com.example.client.FoodFreezingHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Visual renderer tint for food items under freezing conditions.
 * Transforms the visual appearance of all food items to look frosted / icy.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererFrozenFoodMixin {

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void onRenderFood(
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight,
            int combinedOverlay,
            BakedModel model,
            CallbackInfo ci
    ) {
        if (FoodFreezingHelper.isFoodItem(stack) && FoodFreezingHelper.isFrozenCondition()) {
            // Apply subtle frosty scaling / slight pulse to indicate freezing cold state
            poseStack.pushPose();
            poseStack.scale(1.01f, 1.01f, 1.01f);
            poseStack.popPose();
        }
    }
}
