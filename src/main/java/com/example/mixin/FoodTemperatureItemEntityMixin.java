package com.example.mixin;

import com.example.util.FoodTemperatureHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures dropped food items freeze when on cold ground or in cold environments.
 */
@Mixin(ItemEntity.class)
public class FoodTemperatureItemEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hypothermia$tickDroppedFood(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) return;
        if (entity.tickCount % 40 != 0) return;

        ItemStack stack = entity.getItem();
        if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
            double ambientTemp = FoodTemperatureHelper.getAmbientTemperature(level, entity.blockPosition());
            FoodTemperatureHelper.tickFood(stack, ambientTemp);
        }
    }
}
