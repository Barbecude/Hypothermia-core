package com.example;

import com.example.util.FoodTemperatureHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "hypothermia_core";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[HypothermiaCore] Initializing Hypothermia Core...");

		// Register /freezefood and /freeze_food commands
		CommandRegistrationCallback.EVENT.register(FoodTemperatureHelper::registerCommands);

		// Disable FIAHI's broken 20x/sec inventory tick so unified food temp runs consistently
		FoodTemperatureHelper.disableFiahiBrokenTick();

		LOGGER.info("[HypothermiaCore] Registered food temperature system and /freezefood commands!");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
