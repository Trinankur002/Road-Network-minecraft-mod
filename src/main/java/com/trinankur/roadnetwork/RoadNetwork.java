package com.trinankur.roadnetwork;

import com.trinankur.roadnetwork.command.RoadNetworkCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoadNetwork implements ModInitializer {
	public static final String MOD_ID = "road-network";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Road Network mod (Milestone M4: Autonomous Transportation Network)...");

		// Register lifecycle event handler for dynamic destination validation and chunk road materialization
		com.trinankur.roadnetwork.generation.RoadLifecycleHandler.register();

		// Register autonomous background exploration, planning, and road generation
		com.trinankur.roadnetwork.autonomous.AutonomousNetworkManager.register();

		// Register debug commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			RoadNetworkCommand.register(dispatcher, registryAccess);
		});

		LOGGER.info("Road Network mod initialized successfully.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
