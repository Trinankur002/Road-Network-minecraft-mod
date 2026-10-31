package com.trinankur.roadnetwork.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.trinankur.roadnetwork.discovery.DiscoveredStructure;
import com.trinankur.roadnetwork.discovery.DiscoveryStatus;
import com.trinankur.roadnetwork.discovery.StructureClassification;
import com.trinankur.roadnetwork.discovery.StructurePlacementScanner;
import com.trinankur.roadnetwork.discovery.StructureRegistryInspector;
import com.trinankur.roadnetwork.generation.RoadChunkMaterializer;
import com.trinankur.roadnetwork.network.RoadNetworkManager;
import com.trinankur.roadnetwork.network.node.NetworkNode;
import com.trinankur.roadnetwork.network.planning.NetworkPlanner;
import com.trinankur.roadnetwork.network.route.NetworkRoute;
import com.trinankur.roadnetwork.network.route.NetworkWaypoint;
import com.trinankur.roadnetwork.network.storage.PhysicalRouteData;
import com.trinankur.roadnetwork.network.storage.RoadNetworkSavedData;
import com.trinankur.roadnetwork.pathfinding.PhysicalPath;
import com.trinankur.roadnetwork.pathfinding.TerrainPathfinder;
import com.trinankur.roadnetwork.pathfinding.WorldGenTerrainSampler;
import com.trinankur.roadnetwork.util.DimensionValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Command handler for /roadnetwork commands.
 * Provides debugging and management for structure discovery, persistent network graphs, and route planning.
 * Enforces strict Overworld dimension boundaries.
 */
public final class RoadNetworkCommand {

	private static final int DEFAULT_SCAN_RADIUS_CHUNKS = 32;
	private static final int MAX_SCAN_RADIUS_CHUNKS = 128;
	private static final double DEFAULT_PLAN_MAX_DISTANCE = 2000.0;

	private RoadNetworkCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(
			Commands.literal("roadnetwork")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(
					Commands.literal("scan")
						.executes(ctx -> executeScan(ctx, DEFAULT_SCAN_RADIUS_CHUNKS, false))
						.then(
							Commands.argument("radius", IntegerArgumentType.integer(1, MAX_SCAN_RADIUS_CHUNKS))
								.executes(ctx -> executeScan(ctx, IntegerArgumentType.getInteger(ctx, "radius"), false))
								.then(
									Commands.argument("includeIgnored", BoolArgumentType.bool())
										.executes(ctx -> executeScan(
											ctx,
											IntegerArgumentType.getInteger(ctx, "radius"),
											BoolArgumentType.getBool(ctx, "includeIgnored")
										))
								)
						)
				)
				.then(
					Commands.literal("network")
						.executes(RoadNetworkCommand::executeNetwork)
				)
				.then(
					Commands.literal("routes")
						.executes(RoadNetworkCommand::executeRoutes)
				)
				.then(
					Commands.literal("route")
						.then(
							Commands.argument("id", StringArgumentType.string())
								.executes(ctx -> executeRouteDetails(ctx, StringArgumentType.getString(ctx, "id")))
						)
				)
				.then(
					Commands.literal("plan")
						.executes(ctx -> executePlan(ctx, DEFAULT_PLAN_MAX_DISTANCE))
						.then(
							Commands.argument("maxDistance", DoubleArgumentType.doubleArg(100.0, 10000.0))
								.executes(ctx -> executePlan(ctx, DoubleArgumentType.getDouble(ctx, "maxDistance")))
						)
				)
				.then(
					Commands.literal("inspect")
						.executes(RoadNetworkCommand::executeInspect)
				)
				.then(
					Commands.literal("autonomous")
						.executes(RoadNetworkCommand::executeAutonomousStatus)
				)
				.then(
					Commands.literal("diagnose")
						.executes(RoadNetworkCommand::executeDiagnose)
				)
				.then(
					Commands.literal("test-dev")
						.executes(ctx -> executeTestDev(ctx, 64))
						.then(
							Commands.argument("distance", IntegerArgumentType.integer(10, 500))
								.executes(ctx -> executeTestDev(ctx, IntegerArgumentType.getInteger(ctx, "distance")))
						)
						.then(
							Commands.argument("x1", IntegerArgumentType.integer())
								.then(Commands.argument("z1", IntegerArgumentType.integer())
									.then(Commands.argument("x2", IntegerArgumentType.integer())
										.then(Commands.argument("z2", IntegerArgumentType.integer())
											.executes(ctx -> executeTestDevCoords(
												ctx,
												IntegerArgumentType.getInteger(ctx, "x1"),
												IntegerArgumentType.getInteger(ctx, "z1"),
												IntegerArgumentType.getInteger(ctx, "x2"),
												IntegerArgumentType.getInteger(ctx, "z2")
											))
										)
									)
								)
						)
				)
				.then(
					Commands.literal("build")
						.then(
							Commands.argument("id", StringArgumentType.string())
								.executes(ctx -> executeBuild(ctx, StringArgumentType.getString(ctx, "id")))
						)
				)
				.then(
					Commands.literal("test-road")
						.executes(RoadNetworkCommand::executeTestRoad)
				)
				.then(
					Commands.literal("help")
						.executes(RoadNetworkCommand::executeHelp)
				)
		);
	}

	private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal("=== Road Network Commands ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork scan [radius] [includeIgnored]")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Scans nearby structures and registers network nodes.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork network")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Displays persistent network graph summary (nodes, edges, routes).").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork routes")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Lists all planned road routes in the network.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork route <id>")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Inspect details, endpoints, and waypoints for a specific route.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork plan [maxDistance]")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Computes deterministic route connections between major settlements.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork inspect")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Enumerate registered structure sets and placements.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork autonomous")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Displays status of background discovery, planning, and chunk progress.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork diagnose")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Diagnostic breakdown of route construction readiness & blocking reasons.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork test-dev [distance | x1 z1 x2 z2]")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - [DEV TEST] Test real pathfinding & block materialization locally in loaded terrain.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork build <id>")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Calculate terrain-aware path and materialize road blocks in loaded chunks.").withStyle(ChatFormatting.GRAY)), false);
		source.sendSuccess(() -> Component.literal("/roadnetwork test-road")
			.withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - Acceptance test: automatically build the first confirmed settlement route.").withStyle(ChatFormatting.GRAY)), false);
		return 1;
	}

	private static int executeNetwork(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		Collection<NetworkNode> nodes = data.getNodes();
		Collection<NetworkRoute> routes = data.getRoutes();

		long majorCount = nodes.stream().filter(n -> n.classification() == StructureClassification.MAJOR).count();
		long secondaryCount = nodes.stream().filter(n -> n.classification() == StructureClassification.SECONDARY).count();
		long confirmedCount = nodes.stream().filter(n -> n.status() == DiscoveryStatus.CONFIRMED).count();
		long predictedCount = nodes.stream().filter(n -> n.status() == DiscoveryStatus.PREDICTED).count();

		double totalLength = routes.stream().mapToDouble(NetworkRoute::length).sum();

		source.sendSuccess(() -> Component.literal("=== Road Network Graph Summary ===")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal(String.format(
			"• Nodes: %d total (%d Major Settlements, %d Secondary) [%d Confirmed, %d Predicted]",
			nodes.size(), majorCount, secondaryCount, confirmedCount, predictedCount
		)).withStyle(ChatFormatting.YELLOW), false);
		source.sendSuccess(() -> Component.literal(String.format(
			"• Edges: %d road edges connecting settlements", data.getEdgeCount()
		)).withStyle(ChatFormatting.AQUA), false);
		source.sendSuccess(() -> Component.literal(String.format(
			"• Routes: %d planned routes (Total network length: ~%dm)", routes.size(), (int) totalLength
		)).withStyle(ChatFormatting.GREEN), false);
		source.sendSuccess(() -> Component.literal(String.format(
			"• Storage Version: %d", data.getDataVersion()
		)).withStyle(ChatFormatting.DARK_GRAY), false);

		return 1;
	}

	private static int executeRoutes(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		List<NetworkRoute> routes = data.getRouteList();

		if (routes.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No planned routes in network. Run '/roadnetwork plan' to plan connections.")
				.withStyle(ChatFormatting.YELLOW), false);
			return 1;
		}

		source.sendSuccess(() -> Component.literal(String.format("=== Planned Routes (%d) ===", routes.size()))
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

		int index = 1;
		for (NetworkRoute route : routes) {
			String cmd = "/roadnetwork route \"" + route.id() + "\"";
			MutableComponent line = Component.literal(String.format("[%d] ", index++))
				.withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(route.id())
					.withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withBold(true)
						.withClickEvent(new ClickEvent.SuggestCommand(cmd))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to inspect route: " + route.id())))
					))
				.append(Component.literal(String.format(" (~%dm) ", (int) route.length())).withStyle(ChatFormatting.GRAY))
				.append(Component.literal("[" + route.status().getDisplayName() + "]").withStyle(ChatFormatting.GREEN))
				.append(Component.literal(String.format(" (%d waypoints)", route.waypoints().size())).withStyle(ChatFormatting.DARK_GRAY));

			source.sendSuccess(() -> line, false);
		}

		return 1;
	}

	private static int executeRouteDetails(CommandContext<CommandSourceStack> ctx, String routeId) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		Optional<NetworkRoute> optRoute = data.getRoute(routeId);

		if (optRoute.isEmpty()) {
			source.sendFailure(Component.literal("Route not found: " + routeId).withStyle(ChatFormatting.RED));
			return 0;
		}

		NetworkRoute route = optRoute.get();
		Optional<NetworkNode> startNode = data.getNode(route.startNodeId());
		Optional<NetworkNode> endNode = data.getNode(route.endNodeId());

		source.sendSuccess(() -> Component.literal("=== Route Details: " + route.id() + " ===")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("• Status: " + route.status().getDisplayName())
			.withStyle(ChatFormatting.GREEN), false);
		source.sendSuccess(() -> Component.literal("• Classification: " + route.classification().getDisplayName())
			.withStyle(route.classification().getFormatting()), false);
		source.sendSuccess(() -> Component.literal(String.format("• Horizontal Length: ~%dm", (int) route.length()))
			.withStyle(ChatFormatting.YELLOW), false);

		String startCoords = startNode.map(NetworkNode::formatCoordinates).orElse("unknown");
		String endCoords = endNode.map(NetworkNode::formatCoordinates).orElse("unknown");

		source.sendSuccess(() -> Component.literal("• Start: " + route.startNodeId() + " at " + startCoords)
			.withStyle(ChatFormatting.GRAY), false);
		source.sendSuccess(() -> Component.literal("• End:   " + route.endNodeId() + " at " + endCoords)
			.withStyle(ChatFormatting.GRAY), false);

		NetworkRoute.RouteConstructionEligibility eligibility = route.checkConstructionEligibility(
			startNode.orElse(null), endNode.orElse(null)
		);
		ChatFormatting eligColor = eligibility.isReady() ? ChatFormatting.GREEN : ChatFormatting.RED;
		source.sendSuccess(() -> Component.literal("• Construction Safety: " + eligibility.getDescription())
			.withStyle(eligColor), false);

		Optional<PhysicalRouteData> optPhysical = data.getPhysicalRoute(routeId);
		if (optPhysical.isPresent()) {
			PhysicalRouteData pr = optPhysical.get();
			source.sendSuccess(() -> Component.literal(String.format(
				"• Physical Road: Computed (%d points, %d chunks materialized)",
				pr.getPathPoints().size(), pr.getMaterializedCount()
			)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
		} else if (eligibility.isReady()) {
			String buildCmd = "/roadnetwork build \"" + routeId + "\"";
			source.sendSuccess(() -> Component.literal("• Physical Road: Ready to build ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal("[Click to Build Road]")
					.withStyle(style -> style
						.withColor(ChatFormatting.GOLD)
						.withBold(true)
						.withClickEvent(new ClickEvent.SuggestCommand(buildCmd))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run: " + buildCmd)))
					)), false);
		}

		source.sendSuccess(() -> Component.literal(String.format("• Waypoints (%d):", route.waypoints().size()))
			.withStyle(ChatFormatting.AQUA), false);

		int wpIdx = 1;
		for (NetworkWaypoint wp : route.waypoints()) {
			int currentIdx = wpIdx++;
			source.sendSuccess(() -> Component.literal(String.format("    [%d] %s", currentIdx, wp.formatCoordinates()))
				.withStyle(ChatFormatting.WHITE), false);
		}

		return 1;
	}

	private static int executePlan(CommandContext<CommandSourceStack> ctx, double maxDistance) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(String.format(
			"Starting background network planning (max connection distance: %dm)...", (int) maxDistance
		)).withStyle(ChatFormatting.GRAY), false);

		RoadNetworkManager.planNetworkAsync(level, maxDistance).thenAcceptAsync(result -> {
			source.getServer().execute(() -> {
				if (!DimensionValidator.isSupported(source.getLevel())) {
					source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
					return;
				}

				RoadNetworkSavedData data = RoadNetworkSavedData.get(source.getLevel());
				int added = result.newEdges().size();
				int removed = result.removedEdgeKeys().size();
				String changesSummary = result.hasChanges()
					? String.format("Reconciled: +%d new, -%d obsolete", added, removed)
					: "Network is up to date (0 changes)";

				source.sendSuccess(() -> Component.literal(String.format(
					"Network planning completed! Evaluated %d settlement nodes. %s (%d total routes in network).",
					result.nodesEvaluated(), changesSummary, data.getRouteCount()
				)).withStyle(result.hasChanges() ? ChatFormatting.GREEN : ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
			});
		}, source.getServer());

		return 1;
	}

	private static int executeInspect(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendSuccess(() -> Component.literal("[Notice] " + DimensionValidator.UNSUPPORTED_DIMENSION_MESSAGE + " Displaying static registry definitions only.")
				.withStyle(ChatFormatting.YELLOW), false);
		}

		source.sendSuccess(() -> Component.literal("Inspecting structure registries...").withStyle(ChatFormatting.GRAY), false);

		List<StructureRegistryInspector.StructureSetInfo> sets = StructureRegistryInspector.inspectStructureSets(level);
		List<StructureRegistryInspector.StructureInfo> structures = StructureRegistryInspector.inspectStructures(level);

		source.sendSuccess(() -> Component.literal(String.format("=== Registered Structure Sets (%d) ===", sets.size()))
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

		for (StructureRegistryInspector.StructureSetInfo info : sets) {
			MutableComponent setLine = Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(info.setId().toString()).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(String.format(" [%s] ", info.placementType())).withStyle(ChatFormatting.AQUA))
				.append(Component.literal("(" + info.placementSummary() + ")").withStyle(ChatFormatting.DARK_GRAY));

			source.sendSuccess(() -> setLine, false);

			for (StructureRegistryInspector.StructureSetEntryInfo entry : info.structures()) {
				MutableComponent structLine = Component.literal("    - ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(entry.structureId() != null ? entry.structureId().toString() : "unknown").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(String.format(" (weight: %d) ", entry.weight())).withStyle(ChatFormatting.GRAY))
					.append(Component.literal("[" + entry.classification().getDisplayName() + "]").withStyle(entry.classification().getFormatting()));

				source.sendSuccess(() -> structLine, false);
			}
		}

		source.sendSuccess(() -> Component.literal(String.format("Total registered structures: %d", structures.size()))
			.withStyle(ChatFormatting.GREEN), false);

		return 1;
	}

	private static int executeScan(CommandContext<CommandSourceStack> ctx, int radiusChunks, boolean includeIgnored) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();

		// Enforce strict dimension restriction
		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		BlockPos origin = BlockPos.containing(source.getPosition());

		source.sendSuccess(() -> Component.literal(String.format(
			"Starting structure scan: radius %d chunks (~%d blocks) around [X: %d, Z: %d]...",
			radiusChunks, radiusChunks * 16, origin.getX(), origin.getZ()
		)).withStyle(ChatFormatting.GRAY), false);

		StructurePlacementScanner.ScanConfig config = new StructurePlacementScanner.ScanConfig(
			radiusChunks,
			includeIgnored,
			50
		);

		StructurePlacementScanner.scanAsync(level, origin, config).thenAcceptAsync(results -> {
			source.getServer().execute(() -> {
				// Re-verify dimension when delivering results
				if (!DimensionValidator.isSupported(source.getLevel())) {
					source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
					return;
				}

				// Automatically ingest discovered structures into persistent network graph
				int updatedNodes = RoadNetworkManager.ingestDiscoveredStructures(source.getLevel(), results);

				displayScanResults(source, origin, radiusChunks, results);

				if (updatedNodes > 0) {
					source.sendSuccess(() -> Component.literal(String.format(
						"Registered %d structure nodes into persistent network graph. Run '/roadnetwork network' to view.",
						updatedNodes
					)).withStyle(ChatFormatting.DARK_GREEN), false);
				}
			});
		}, source.getServer());

		return 1;
	}

	private static void displayScanResults(
		CommandSourceStack source,
		BlockPos origin,
		int radiusChunks,
		List<DiscoveredStructure> results
	) {
		int total = results.size();
		long confirmedCount = results.stream().filter(s -> s.status() == DiscoveryStatus.CONFIRMED).count();
		long predictedCount = results.stream().filter(s -> s.status() == DiscoveryStatus.PREDICTED).count();

		MutableComponent header = Component.literal(String.format(
			"=== Structure Scan: %d found (%d confirmed, %d predicted) ===",
			total, confirmedCount, predictedCount
		)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

		source.sendSuccess(() -> header, false);

		if (results.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No structures found within search radius.").withStyle(ChatFormatting.YELLOW), false);
			return;
		}

		int index = 1;
		for (DiscoveredStructure structure : results) {
			String tpCommand;
			if (structure.hasKnownY()) {
				tpCommand = String.format("/tp @s %d %d %d", structure.x(), structure.knownY().getAsInt(), structure.z());
			} else {
				tpCommand = String.format("/tp @s %d ~ %d", structure.x(), structure.z());
			}

			String coordsFormatted = structure.formatCoordinates();

			MutableComponent line = Component.literal(String.format("[%d] ", index++))
				.withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(structure.id() != null ? structure.id().toString() : "unknown")
					.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
				.append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(coordsFormatted)
					.withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.SuggestCommand(tpCommand))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to suggest teleport command:\n" + tpCommand)))
					))
				.append(Component.literal(String.format(" (~%dm) ", (int) structure.distance())).withStyle(ChatFormatting.GRAY))
				.append(Component.literal("[" + structure.classification().getDisplayName() + "] ")
					.withStyle(structure.classification().getFormatting()))
				.append(Component.literal("[" + structure.status().getLabel() + "]")
					.withStyle(structure.status().getFormatting()))
				.append(Component.literal(" (" + structure.placementType() + ")").withStyle(ChatFormatting.DARK_GRAY));

			source.sendSuccess(() -> line, false);
		}
	}

	private static int executeBuild(CommandContext<CommandSourceStack> context, String routeId) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		NetworkRoute route = data.getRoute(routeId).orElse(null);
		if (route == null) {
			source.sendFailure(Component.literal("Route not found: " + routeId).withStyle(ChatFormatting.RED));
			return 0;
		}

		NetworkNode startNode = data.getNode(route.startNodeId()).orElse(null);
		NetworkNode endNode = data.getNode(route.endNodeId()).orElse(null);

		NetworkRoute.RouteConstructionEligibility eligibility = route.checkConstructionEligibility(startNode, endNode);
		if (!eligibility.isReady()) {
			source.sendFailure(Component.literal(String.format(
				"Cannot build physical road: %s\nStart: %s (%s)\nEnd: %s (%s)",
				eligibility.getDescription(),
				startNode != null ? startNode.formatCoordinates() : "null",
				startNode != null ? startNode.status().getLabel() : "missing",
				endNode != null ? endNode.formatCoordinates() : "null",
				endNode != null ? endNode.status().getLabel() : "missing"
			)).withStyle(ChatFormatting.RED));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(String.format(
			"Calculating terrain-aware path for route %s...", routeId
		)).withStyle(ChatFormatting.YELLOW), false);

		// Get or compute physical route path
		PhysicalRouteData physicalData = data.getPhysicalRoute(routeId).orElse(null);
		PhysicalPath physicalPath;
		if (physicalData == null) {
			WorldGenTerrainSampler sampler = new WorldGenTerrainSampler(level);
			BlockPos startPos = new BlockPos(startNode.x(), startNode.elevation().get(), startNode.z());
			BlockPos endPos = new BlockPos(endNode.x(), endNode.elevation().get(), endNode.z());
			physicalPath = TerrainPathfinder.findPath(startPos, endPos, sampler);
			physicalData = new PhysicalRouteData(routeId, physicalPath.getPoints());
			data.savePhysicalRoute(physicalData);
		} else {
			physicalPath = new PhysicalPath(physicalData.getPathPoints());
		}

		// Materialize all currently loaded chunks along the path
		int materializedNow = 0;
		int totalBlocksPlaced = 0;
		for (ChunkPos cp : physicalPath.getIntersectingChunks()) {
			if (!physicalData.isMaterializedIn(cp)) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x(), cp.z());
				if (chunk != null) {
					int placed = RoadChunkMaterializer.materializeChunk(level, chunk, physicalPath.getPointsForChunk(cp));
					physicalData.markChunkMaterialized(cp);
					totalBlocksPlaced += placed;
					materializedNow++;
				}
			}
		}

		if (materializedNow > 0) {
			data.setDirty();
		}

		int totalChunks = physicalPath.getIntersectingChunks().size();
		int totalMaterializedChunks = physicalData.getMaterializedCount();
		final int finalBlocksPlaced = totalBlocksPlaced;
		final int finalMaterializedNow = materializedNow;

		source.sendSuccess(() -> Component.literal(String.format(
			"Road %s: %d waypoints spanning %d chunks.\nMaterialized %d blocks across %d loaded chunks (Total progress: %d/%d chunks).\nRemaining chunks will materialize automatically as players load them.",
			routeId, physicalPath.size(), totalChunks, finalBlocksPlaced, finalMaterializedNow, totalMaterializedChunks, totalChunks
		)).withStyle(ChatFormatting.GREEN), true);

		return 1;
	}

	private static int executeTestRoad(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);

		// Find the first eligible route connecting confirmed settlements
		NetworkRoute targetRoute = null;
		int evaluatedCount = 0;
		int rejectedPredictedCount = 0;
		int rejectedElevationCount = 0;
		int rejectedInvalidCount = 0;

		for (NetworkRoute route : data.getRoutes()) {
			evaluatedCount++;
			NetworkNode start = data.getNode(route.startNodeId()).orElse(null);
			NetworkNode end = data.getNode(route.endNodeId()).orElse(null);
			NetworkRoute.RouteConstructionEligibility elig = route.checkConstructionEligibility(start, end);

			if (elig.isReady()) {
				targetRoute = route;
				break;
			} else if (elig == NetworkRoute.RouteConstructionEligibility.BLOCKED_PREDICTED_ENDPOINTS) {
				rejectedPredictedCount++;
			} else if (elig == NetworkRoute.RouteConstructionEligibility.BLOCKED_UNKNOWN_ELEVATION) {
				rejectedElevationCount++;
			} else {
				rejectedInvalidCount++;
			}
		}

		if (targetRoute == null) {
			long confirmedWithElevCount = data.getNodes().stream()
				.filter(n -> n.status() == DiscoveryStatus.CONFIRMED && n.hasKnownElevation()).count();
			long totalConfirmedCount = data.getNodes().stream()
				.filter(n -> n.status() == DiscoveryStatus.CONFIRMED).count();
			long predictedCount = data.getNodes().stream()
				.filter(n -> n.status() == DiscoveryStatus.PREDICTED).count();

			MutableComponent message = Component.literal("=== Test-Road Evaluation Failed ===")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal(String.format("\nEvaluated %d planned routes:", evaluatedCount)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(String.format("\n  • %d routes rejected: One or both destination structures are predicted candidates.", rejectedPredictedCount)).withStyle(ChatFormatting.RED))
				.append(Component.literal(String.format("\n  • %d routes rejected: Confirmed structure but unknown elevation.", rejectedElevationCount)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(String.format("\nSettlement Network Status:\n  • Confirmed with known elevation: %d\n  • Total confirmed: %d\n  • Predicted candidates: %d",
					confirmedWithElevCount, totalConfirmedCount, predictedCount)).withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\nSafety Rule: Physical road construction requires at least TWO confirmed settlements with validated 3D elevations.").withStyle(ChatFormatting.WHITE));

			// Suggest first predicted candidate
			NetworkNode firstPredicted = data.getNodes().stream()
				.filter(n -> n.status() == DiscoveryStatus.PREDICTED)
				.findFirst().orElse(null);

			if (firstPredicted != null) {
				String tpCmd = String.format("/tp @s %d ~ %d", firstPredicted.x(), firstPredicted.z());
				message.append(Component.literal("\n\nRecommended Next Steps:").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
					.append(Component.literal(String.format("\n1. Travel to predicted candidate %s at [X: %d, Z: %d] ", firstPredicted.structureId(), firstPredicted.x(), firstPredicted.z())).withStyle(ChatFormatting.WHITE))
					.append(Component.literal("[Click to Teleport]").withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withBold(true)
						.withClickEvent(new ClickEvent.SuggestCommand(tpCmd))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run: " + tpCmd)))
					))
					.append(Component.literal("\n2. Once chunks load, run '/roadnetwork scan 16' to confirm it."))
					.append(Component.literal("\n3. Re-run '/roadnetwork test-road' once at least 2 settlements are confirmed."))
					.append(Component.literal("\n\nOr test road generation immediately in loaded terrain using:")
						.withStyle(ChatFormatting.LIGHT_PURPLE))
					.append(Component.literal("\n   /roadnetwork test-dev")
						.withStyle(style -> style
							.withColor(ChatFormatting.YELLOW)
							.withBold(true)
							.withClickEvent(new ClickEvent.SuggestCommand("/roadnetwork test-dev 64"))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to test road generation locally: /roadnetwork test-dev 64")))
						));
			}

			source.sendFailure(message);
			return 0;
		}

		return executeBuild(context, targetRoute.id());
	}

	private static int executeDiagnose(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		List<NetworkRoute> routes = data.getRouteList();
		Collection<NetworkNode> nodes = data.getNodes();

		long confirmedNodesCount = nodes.stream().filter(n -> n.status() == DiscoveryStatus.CONFIRMED).count();
		long confirmedKnownElevationCount = nodes.stream().filter(n -> n.status() == DiscoveryStatus.CONFIRMED && n.hasKnownElevation()).count();
		long predictedNodesCount = nodes.stream().filter(n -> n.status() == DiscoveryStatus.PREDICTED).count();

		List<NetworkRoute> readyRoutes = new ArrayList<>();
		List<NetworkRoute> predictedEndpointRoutes = new ArrayList<>();
		List<NetworkRoute> unknownElevationRoutes = new ArrayList<>();
		List<NetworkRoute> invalidRoutes = new ArrayList<>();

		for (NetworkRoute r : routes) {
			NetworkNode start = data.getNode(r.startNodeId()).orElse(null);
			NetworkNode end = data.getNode(r.endNodeId()).orElse(null);

			NetworkRoute.RouteConstructionEligibility eligibility = r.checkConstructionEligibility(start, end);
			switch (eligibility) {
				case READY -> readyRoutes.add(r);
				case BLOCKED_PREDICTED_ENDPOINTS -> predictedEndpointRoutes.add(r);
				case BLOCKED_UNKNOWN_ELEVATION -> unknownElevationRoutes.add(r);
				case INVALID_ENDPOINTS -> invalidRoutes.add(r);
			}
		}

		source.sendSuccess(() -> Component.literal("=== Road Network Construction Diagnostics ===")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

		source.sendSuccess(() -> Component.literal(String.format(
			"• Settlement Nodes: %d total (%d Confirmed [%d with known elevation], %d Predicted)",
			nodes.size(), confirmedNodesCount, confirmedKnownElevationCount, predictedNodesCount
		)).withStyle(ChatFormatting.WHITE), false);

		source.sendSuccess(() -> Component.literal(String.format(
			"• Planned Routes: %d total", routes.size()
		)).withStyle(ChatFormatting.WHITE), false);

		source.sendSuccess(() -> Component.literal(String.format(
			"  - Ready for construction: %d", readyRoutes.size()
		)).withStyle(readyRoutes.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN), false);

		source.sendSuccess(() -> Component.literal(String.format(
			"  - Blocked by predicted endpoints: %d", predictedEndpointRoutes.size()
		)).withStyle(predictedEndpointRoutes.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.RED), false);

		source.sendSuccess(() -> Component.literal(String.format(
			"  - Blocked by unknown elevation: %d", unknownElevationRoutes.size()
		)).withStyle(unknownElevationRoutes.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.YELLOW), false);

		// Materialization & physical progress summary
		long computedPhysicalRoutes = data.getPhysicalRoutes().size();
		int totalMaterializedChunksAll = data.getPhysicalRoutes().stream()
			.mapToInt(PhysicalRouteData::getMaterializedCount).sum();

		source.sendSuccess(() -> Component.literal(String.format(
			"• Physical Progress: %d computed routes, %d total materialized chunks",
			computedPhysicalRoutes, totalMaterializedChunksAll
		)).withStyle(ChatFormatting.AQUA), false);

		// Detailed per-route breakdown
		source.sendSuccess(() -> Component.literal("--- Route Details & Blocking Reasons ---")
			.withStyle(ChatFormatting.DARK_GRAY), false);

		if (routes.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No routes in network graph. Run '/roadnetwork plan' to plan connections.")
				.withStyle(ChatFormatting.YELLOW), false);
			return 1;
		}

		int idx = 1;
		for (NetworkRoute r : routes) {
			NetworkNode start = data.getNode(r.startNodeId()).orElse(null);
			NetworkNode end = data.getNode(r.endNodeId()).orElse(null);
			NetworkRoute.RouteConstructionEligibility elig = r.checkConstructionEligibility(start, end);

			Optional<PhysicalRouteData> optPhysical = data.getPhysicalRoute(r.id());
			String physicalInfo = optPhysical.map(pr -> String.format(" [Physical: %d pts, %d chunks]", pr.getPathPoints().size(), pr.getMaterializedCount()))
				.orElse(" [Physical: Not computed]");

			ChatFormatting statusColor = elig.isReady() ? ChatFormatting.GREEN : (elig == NetworkRoute.RouteConstructionEligibility.BLOCKED_PREDICTED_ENDPOINTS ? ChatFormatting.RED : ChatFormatting.YELLOW);

			MutableComponent line = Component.literal(String.format("[%d] %s", idx++, r.id()))
				.withStyle(ChatFormatting.WHITE)
				.append(Component.literal(" -> " + elig.getDescription()).withStyle(statusColor))
				.append(Component.literal(physicalInfo).withStyle(ChatFormatting.GRAY));

			source.sendSuccess(() -> line, false);

			// If blocked, display specific endpoint diagnostic
			if (!elig.isReady()) {
				String startDesc = formatNodeDiagnostic(start, r.startNodeId());
				String endDesc = formatNodeDiagnostic(end, r.endNodeId());
				source.sendSuccess(() -> Component.literal("      Start: " + startDesc).withStyle(ChatFormatting.GRAY), false);
				source.sendSuccess(() -> Component.literal("      End:   " + endDesc).withStyle(ChatFormatting.GRAY), false);
			}
		}

		return 1;
	}

	private static String formatNodeDiagnostic(NetworkNode node, String id) {
		if (node == null) {
			return id + " (MISSING from network)";
		}
		String status = node.status() == DiscoveryStatus.CONFIRMED ? "CONFIRMED" : "PREDICTED";
		String elev = node.hasKnownElevation() ? ("Y=" + node.elevation().get()) : "Y=unknown";
		return String.format("%s [%s, %s] at [X: %d, %s, Z: %d]",
			node.structureId(), status, elev, node.x(), elev, node.z());
	}

	private static int executeTestDev(CommandContext<CommandSourceStack> context, int distance) {
		CommandSourceStack source = context.getSource();
		BlockPos center = BlockPos.containing(source.getPosition());
		int startX = center.getX();
		int startZ = center.getZ();

		// Default offset along +X and +Z
		int endX = startX + distance;
		int endZ = startZ + (distance / 2);

		return executeTestDevCoords(context, startX, startZ, endX, endZ);
	}

	private static int executeTestDevCoords(CommandContext<CommandSourceStack> context, int startX, int startZ, int endX, int endZ) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(
			"=== [DEVELOPMENT TEST] Running Controlled Road Materialization ==="
		).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);

		WorldGenTerrainSampler sampler = new WorldGenTerrainSampler(level);
		int startY = sampler.getSurfaceHeight(startX, startZ);
		int endY = sampler.getSurfaceHeight(endX, endZ);

		BlockPos startPos = new BlockPos(startX, startY, startZ);
		BlockPos endPos = new BlockPos(endX, endY, endZ);

		double distance = Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endZ - startZ, 2));

		source.sendSuccess(() -> Component.literal(String.format(
			"• Test Route: from [X: %d, Y: %d, Z: %d] to [X: %d, Y: %d, Z: %d] (~%dm)",
			startX, startY, startZ, endX, endY, endZ, (int) distance
		)).withStyle(ChatFormatting.WHITE), false);

		// Compute terrain-aware path using production TerrainPathfinder
		PhysicalPath physicalPath = TerrainPathfinder.findPath(startPos, endPos, sampler);

		// Register as dev-test route in SavedData so CHUNK_LOAD materializes remaining chunks if player moves
		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		String devRouteId = String.format("route_dev_test_%d_%d__%d_%d", startX, startZ, endX, endZ);
		PhysicalRouteData physicalData = new PhysicalRouteData(devRouteId, physicalPath.getPoints());
		data.savePhysicalRoute(physicalData);

		// Materialize in all currently loaded chunks
		int materializedNow = 0;
		int totalBlocksPlaced = 0;
		for (ChunkPos cp : physicalPath.getIntersectingChunks()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x(), cp.z());
			if (chunk != null) {
				int placed = RoadChunkMaterializer.materializeChunk(level, chunk, physicalPath.getPointsForChunk(cp));
				physicalData.markChunkMaterialized(cp);
				totalBlocksPlaced += placed;
				materializedNow++;
			}
		}

		if (materializedNow > 0) {
			data.setDirty();
		}

		final int finalBlocksPlaced = totalBlocksPlaced;
		final int finalMaterializedNow = materializedNow;
		final int totalChunks = physicalPath.getIntersectingChunks().size();

		source.sendSuccess(() -> Component.literal(String.format(
			"• Result: %d path waypoints across %d chunks.\n• Materialized %d road blocks across %d loaded chunks (Total: %d/%d chunks).\n• Disclaimer: This is a controlled development test in loaded terrain. Production settlement roads continue to strictly enforce confirmed endpoint requirements.",
			physicalPath.size(), totalChunks, finalBlocksPlaced, finalMaterializedNow, physicalData.getMaterializedCount(), totalChunks
		)).withStyle(ChatFormatting.GREEN), true);

		String tpStart = String.format("/tp @s %d %d %d", startX, startY + 1, startZ);
		String tpEnd = String.format("/tp @s %d %d %d", endX, endY + 1, endZ);

		source.sendSuccess(() -> Component.literal("  [Teleport to Start]")
			.withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.SuggestCommand(tpStart))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run: " + tpStart)))
			)
			.append(Component.literal("  "))
			.append(Component.literal("[Teleport to End]")
				.withStyle(style -> style
					.withColor(ChatFormatting.AQUA)
					.withClickEvent(new ClickEvent.SuggestCommand(tpEnd))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run: " + tpEnd)))
				)
			), false);

		return 1;
	}

	private static int executeAutonomousStatus(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();

		if (!DimensionValidator.isSupported(level)) {
			source.sendFailure(DimensionValidator.getUnsupportedMessage().copy().withStyle(ChatFormatting.RED));
			return 0;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);

		long confirmedCount = data.getNodes().stream().filter(n -> n.status() == DiscoveryStatus.CONFIRMED).count();
		long predictedCount = data.getNodes().stream().filter(n -> n.status() == DiscoveryStatus.PREDICTED).count();
		int totalMaterializedChunks = data.getPhysicalRoutes().stream().mapToInt(PhysicalRouteData::getMaterializedCount).sum();

		source.sendSuccess(() -> Component.literal("=== Autonomous Transportation Network Status ===")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("• Background Exploration Service: ACTIVE (checks periodically as players move)")
			.withStyle(ChatFormatting.GREEN), false);
		source.sendSuccess(() -> Component.literal(String.format(
			"• Scanned Regions: %d regions (512x512m blocks each)",
			data.getScannedRegions().size()
		)).withStyle(ChatFormatting.WHITE), false);
		source.sendSuccess(() -> Component.literal(String.format(
			"• Settlement Nodes: %d total (%d Confirmed, %d Predicted)",
			data.getNodeCount(), confirmedCount, predictedCount
		)).withStyle(ChatFormatting.WHITE), false);
		source.sendSuccess(() -> Component.literal(String.format(
			"• Planned Routes: %d routes (%d with pre-calculated physical paths)",
			data.getRouteCount(), data.getPhysicalRoutes().size()
		)).withStyle(ChatFormatting.WHITE), false);
		source.sendSuccess(() -> Component.literal(String.format(
			"• Physical Road Materialization: %d chunks materialized in world",
			totalMaterializedChunks
		)).withStyle(ChatFormatting.AQUA), false);
		source.sendSuccess(() -> Component.literal(
			"• Autonomous Philosophy: In normal survival gameplay, no commands are required. The transportation network discovers settlements, plans routes, and materializes roads automatically as you explore."
		).withStyle(ChatFormatting.GRAY), false);

		return 1;
	}
}
