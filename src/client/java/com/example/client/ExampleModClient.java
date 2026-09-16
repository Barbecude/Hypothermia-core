package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class ExampleModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		IrlRedactorConfigPersistence.init();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			IrlRedactorConfigPersistence.loadConfig();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			IrlRedactorConfigPersistence.saveAll();
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			IrlRedactorConfigPersistence.saveAll();
		});
	}
}