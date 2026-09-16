package com.example.client.mixin;

import com.example.client.FoodFreezingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Task 1 Mandatory Feature: Serene Seasons & Cold Sweat food freezing integration.
 * Detects winter / sub-zero freezing conditions and marks food items as "Frozen" (Beku).
 */
@Mixin(ItemStack.class)
public class SereneSeasonsFoodMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void addFrozenFoodTooltip(
            Item.TooltipContext context,
            Player player,
            TooltipFlag tooltipFlag,
            CallbackInfoReturnable<List<Component>> cir
    ) {
        ItemStack stack = (ItemStack)(Object)this;
        if (FoodFreezingHelper.isFoodItem(stack) && FoodFreezingHelper.isFrozenCondition()) {
            List<Component> tooltip = cir.getReturnValue();
            if (tooltip != null) {
                tooltip.add(Component.literal("❄ Beku (Frozen)").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                tooltip.add(Component.literal("Suhu dingin Serene Seasons membekukan makanan ini.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }
}
