package com.example.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = GuiGraphics.class, priority = 2000)
public abstract class GuiGraphicsFoodModelMixin {
    @ModifyVariable(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
        at = @At("STORE"),
        ordinal = 0
    )
    private BakedModel hypothermia$ensureGuiFoodModelOverrides(BakedModel model, LivingEntity entity, Level level, ItemStack stack, int x, int y, int z, int seed) {
        if (model != null && stack != null && !stack.isEmpty()) {
            ItemOverrides overrides = model.getOverrides();
            if (overrides != null && overrides != ItemOverrides.EMPTY) {
                ClientLevel clientLevel = level instanceof ClientLevel cl ? cl : Minecraft.getInstance().level;
                BakedModel resolved = overrides.resolve(model, stack, clientLevel, entity, seed);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return model;
    }
}
