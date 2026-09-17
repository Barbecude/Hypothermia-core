package com.example.mixin;

import com.example.util.FoodTemperatureHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix for FIAHI food temperature ticking in player inventory.
 * Delegates to FoodTemperatureHelper for consistent ambient temperature and realistic cooling rates.
 */
@Mixin(Inventory.class)
public class FoodTemperatureTickMixin {
    @Shadow @Final
    public Player player;

    @Unique
    private int hypothermia$tickCounter = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void hypothermia$tickFoodTemperature(CallbackInfo ci) {
        if (this.player == null || !(this.player.level() instanceof ServerLevel)) return;

        hypothermia$tickCounter++;
        if (hypothermia$tickCounter < 20) return;
        hypothermia$tickCounter = 0;

        FoodTemperatureHelper.tickInventory(this.player);
    }
}
