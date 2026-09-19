package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class ExampleModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        IrlRedactorConfigPersistence.init();

        // Load settings whenever joining a world.
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> {
                    IrlRedactorConfigPersistence.loadConfig();
                }
        );

        // Save settings when leaving a world.
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    IrlRedactorConfigPersistence.saveAll();
                }
        );
    }
}