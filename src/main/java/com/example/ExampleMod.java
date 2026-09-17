package com.example;

import com.example.util.FoodTemperatureHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "hypothermia_core";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[HypothermiaCore] Initializing Hypothermia Core...");

		// Register server tick event for container temperature ticking
		ServerTickEvents.END_WORLD_TICK.register(FoodTemperatureHelper::onWorldTick);

		// Register /freezefood and /freeze_food commands
		CommandRegistrationCallback.EVENT.register(FoodTemperatureHelper::registerCommands);

		LOGGER.info("[HypothermiaCore] Registered food temperature container ticker and /freezefood command!");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
