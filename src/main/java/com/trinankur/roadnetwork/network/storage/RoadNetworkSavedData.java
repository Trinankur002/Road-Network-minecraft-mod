package com.trinankur.roadnetwork.network.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trinankur.roadnetwork.discovery.DiscoveredStructure;
import com.trinankur.roadnetwork.discovery.DiscoveryStatus;
import com.trinankur.roadnetwork.discovery.StructureClassification;
import com.trinankur.roadnetwork.network.graph.NetworkEdge;
import com.trinankur.roadnetwork.network.node.NetworkNode;
import com.trinankur.roadnetwork.network.route.NetworkRoute;
import com.trinankur.roadnetwork.util.DimensionValidator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-level persistent storage for the road network graph.
 * Survives world restarts and saves to the world data directory using SavedData APIs.
 * Supports updating predicted nodes when confirmed, prevents duplicate nodes and edges,
 * and maintains data versioning for future migrations.
 */
public class RoadNetworkSavedData extends SavedData {

	public static final int CURRENT_DATA_VERSION = 1;
	public static final Identifier DATA_ID = Identifier.fromNamespaceAndPath("roadnetwork", "network_data");

	public static final Codec<RoadNetworkSavedData> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			Codec.INT.optionalFieldOf("data_version", CURRENT_DATA_VERSION).forGetter(RoadNetworkSavedData::getDataVersion),
			Codec.list(NetworkNode.CODEC).optionalFieldOf("nodes", List.of()).forGetter(RoadNetworkSavedData::getNodeList),
			Codec.list(NetworkEdge.CODEC).optionalFieldOf("edges", List.of()).forGetter(RoadNetworkSavedData::getEdgeList),
			Codec.list(NetworkRoute.CODEC).optionalFieldOf("routes", List.of()).forGetter(RoadNetworkSavedData::getRouteList),
			Codec.list(PhysicalRouteData.CODEC).optionalFieldOf("physical_routes", List.of()).forGetter(RoadNetworkSavedData::getPhysicalRouteList),
			Codec.list(ChunkPos.CODEC).optionalFieldOf("scanned_regions", List.of()).forGetter(RoadNetworkSavedData::getScannedRegionList),
			Codec.LONG.optionalFieldOf("last_planned_time", 0L).forGetter(RoadNetworkSavedData::getLastPlannedTime)
		).apply(instance, RoadNetworkSavedData::createFromData)
	);

	public static final SavedDataType<RoadNetworkSavedData> TYPE = new SavedDataType<>(
		DATA_ID,
		RoadNetworkSavedData::new,
		CODEC,
		DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private int dataVersion;
	private final Map<String, NetworkNode> nodes = new ConcurrentHashMap<>();
	private final Map<String, NetworkEdge> edges = new ConcurrentHashMap<>();
	private final Map<String, NetworkRoute> routes = new ConcurrentHashMap<>();
	private final Map<String, PhysicalRouteData> physicalRoutes = new ConcurrentHashMap<>();
	private final Set<ChunkPos> scannedRegions = ConcurrentHashMap.newKeySet();
	private long lastPlannedTime;

	public RoadNetworkSavedData() {
		this(CURRENT_DATA_VERSION, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0L);
	}

	public RoadNetworkSavedData(
		int dataVersion,
		List<NetworkNode> nodeList,
		List<NetworkEdge> edgeList,
		List<NetworkRoute> routeList,
		List<PhysicalRouteData> physicalRouteList,
		List<ChunkPos> scannedRegionList,
		long lastPlannedTime
	) {
		this.dataVersion = dataVersion;
		for (NetworkNode node : nodeList) {
			this.nodes.put(node.id(), node);
		}
		for (NetworkEdge edge : edgeList) {
			this.edges.put(edge.edgeKey(), edge);
		}
		for (NetworkRoute route : routeList) {
			this.routes.put(route.id(), route);
		}
		for (PhysicalRouteData pr : physicalRouteList) {
			this.physicalRoutes.put(pr.getRouteId(), pr);
		}
		this.scannedRegions.addAll(scannedRegionList);
		this.lastPlannedTime = lastPlannedTime;
	}

	public RoadNetworkSavedData(
		int dataVersion,
		List<NetworkNode> nodeList,
		List<NetworkEdge> edgeList,
		List<NetworkRoute> routeList,
		List<PhysicalRouteData> physicalRouteList,
		long lastPlannedTime
	) {
		this(dataVersion, nodeList, edgeList, routeList, physicalRouteList, Collections.emptyList(), lastPlannedTime);
	}

	private static RoadNetworkSavedData createFromData(
		int dataVersion,
		List<NetworkNode> nodeList,
		List<NetworkEdge> edgeList,
		List<NetworkRoute> routeList,
		List<PhysicalRouteData> physicalRouteList,
		List<ChunkPos> scannedRegionList,
		long lastPlannedTime
	) {
		return new RoadNetworkSavedData(dataVersion, nodeList, edgeList, routeList, physicalRouteList, scannedRegionList, lastPlannedTime);
	}

	/**
	 * Access the persistent road network data for the given level.
	 * Strictly enforces Overworld dimension boundaries.
	 */
	public static RoadNetworkSavedData get(ServerLevel level) {
		DimensionValidator.validateLevel(level);
		// FIX (teleport persistence): force persistence check before computeIfAbsent
		// Ensures saved-data survives teleport/reload without loader mismatch
		RoadNetworkSavedData existing = level.getDataStorage().get(TYPE);
		if (existing != null) {
			existing.setDirty(); // persist immediately on access
			return existing;
		}
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * Ingests a discovered structure into the network graph as a node.
	 * Excludes ignored or subterranean structures.
	 * Returns true if a new node was added or an existing predicted node was confirmed.
	 */
	public boolean addOrUpdateDiscoveredStructure(DiscoveredStructure structure) {
		if (structure.classification() == StructureClassification.IGNORED) {
			return false;
		}

		NetworkNode incomingNode = NetworkNode.fromDiscoveredStructure(structure);
		return addOrUpdateNode(incomingNode);
	}

	/**
	 * Adds a node or upgrades a predicted node when confirmed.
	 * Returns true if newly added or updated.
	 */
	public boolean addOrUpdateNode(NetworkNode node) {
		NetworkNode existing = this.nodes.get(node.id());
		if (existing == null) {
			this.nodes.put(node.id(), node);
			setDirty();
			return true;
		}

		// Upgrade check: upgrade predicted node to confirmed if validated coordinates become known
		if (existing.status() == DiscoveryStatus.PREDICTED && node.status() == DiscoveryStatus.CONFIRMED) {
			this.nodes.put(node.id(), node);
			setDirty();
			return true;
		}

		return false;
	}

	/**
	 * Updates the known elevation of an existing node when its chunk is loaded and structure bounds are resolved.
	 * Returns true if the node elevation was newly set or changed.
	 */
	public boolean updateNodeElevation(String nodeId, int centerY) {
		NetworkNode existing = this.nodes.get(nodeId);
		if (existing == null) {
			return false;
		}
		if (existing.elevation().isEmpty() || existing.elevation().get() != centerY) {
			NetworkNode updated = new NetworkNode(
				existing.id(),
				existing.structureId(),
				existing.classification(),
				existing.x(),
				existing.z(),
				Optional.of(centerY),
				existing.chunkPos(),
				DiscoveryStatus.CONFIRMED
			);
			this.nodes.put(nodeId, updated);
			setDirty();
			return true;
		}
		return false;
	}

	/**
	 * Saves or replaces the computed physical route geometry.
	 */
	public boolean savePhysicalRoute(PhysicalRouteData physicalRoute) {
		this.physicalRoutes.put(physicalRoute.getRouteId(), physicalRoute);
		setDirty();
		return true;
	}

	public Optional<PhysicalRouteData> getPhysicalRoute(String routeId) {
		return Optional.ofNullable(this.physicalRoutes.get(routeId));
	}

	public Collection<PhysicalRouteData> getPhysicalRoutes() {
		return Collections.unmodifiableCollection(this.physicalRoutes.values());
	}

	public List<PhysicalRouteData> getPhysicalRouteList() {
		return new ArrayList<>(this.physicalRoutes.values());
	}

	public boolean markChunkMaterialized(String routeId, net.minecraft.world.level.ChunkPos chunkPos) {
		PhysicalRouteData pr = this.physicalRoutes.get(routeId);
		if (pr != null && pr.markChunkMaterialized(chunkPos)) {
			setDirty();
			return true;
		}
		return false;
	}

	public boolean isChunkMaterialized(String routeId, net.minecraft.world.level.ChunkPos chunkPos) {
		PhysicalRouteData pr = this.physicalRoutes.get(routeId);
		return pr != null && pr.isMaterializedIn(chunkPos);
	}

	/**
	 * Adds an edge to the network graph, preventing duplicates.
	 */
	public boolean addEdge(NetworkEdge edge) {
		String key = edge.edgeKey();
		if (this.edges.putIfAbsent(key, edge) == null) {
			setDirty();
			return true;
		}
		return false;
	}

	/**
	 * Removes an edge from the persistent graph by key.
	 */
	public boolean removeEdge(String edgeKey) {
		if (this.edges.remove(edgeKey) != null) {
			setDirty();
			return true;
		}
		return false;
	}

	/**
	 * Adds a route to the network graph, preventing duplicates.
	 */
	public boolean addRoute(NetworkRoute route) {
		if (this.routes.putIfAbsent(route.id(), route) == null) {
			setDirty();
			return true;
		}
		return false;
	}

	/**
	 * Removes a route from the persistent graph by route ID.
	 */
	public boolean removeRoute(String routeId) {
		if (this.routes.remove(routeId) != null) {
			setDirty();
			return true;
		}
		return false;
	}

	/**
	 * Applies planning reconciliation changes.
	 * Removes obsolete automatically planned edges/routes, adds new connections, and sets dirty only if changes occurred.
	 */
	public boolean applyReconciliation(com.trinankur.roadnetwork.network.planning.NetworkPlanner.PlanningResult result) {
		if (!result.hasChanges()) {
			return false;
		}

		for (String edgeKey : result.removedEdgeKeys()) {
			this.edges.remove(edgeKey);
		}
		for (String routeId : result.removedRouteIds()) {
			this.routes.remove(routeId);
		}
		for (NetworkEdge edge : result.newEdges()) {
			this.edges.put(edge.edgeKey(), edge);
		}
		for (NetworkRoute route : result.newRoutes()) {
			this.routes.put(route.id(), route);
		}

		this.lastPlannedTime = System.currentTimeMillis();
		setDirty();
		return true;
	}

	public Optional<NetworkNode> getNode(String id) {
		return Optional.ofNullable(this.nodes.get(id));
	}

	public Optional<NetworkRoute> getRoute(String id) {
		return Optional.ofNullable(this.routes.get(id));
	}

	public Collection<NetworkNode> getNodes() {
		return Collections.unmodifiableCollection(this.nodes.values());
	}

	public Collection<NetworkEdge> getEdges() {
		return Collections.unmodifiableCollection(this.edges.values());
	}

	public Collection<NetworkRoute> getRoutes() {
		return Collections.unmodifiableCollection(this.routes.values());
	}

	public List<NetworkNode> getNodeList() {
		return new ArrayList<>(this.nodes.values());
	}

	public List<NetworkEdge> getEdgeList() {
		return new ArrayList<>(this.edges.values());
	}

	public List<NetworkRoute> getRouteList() {
		return new ArrayList<>(this.routes.values());
	}

	public int getDataVersion() {
		return this.dataVersion;
	}

	public long getLastPlannedTime() {
		return this.lastPlannedTime;
	}

	public void setLastPlannedTime(long time) {
		this.lastPlannedTime = time;
		setDirty();
	}

	public int getNodeCount() {
		return this.nodes.size();
	}

	public int getEdgeCount() {
		return this.edges.size();
	}

	public int getRouteCount() {
		return this.routes.size();
	}

	public boolean isRegionScanned(ChunkPos regionPos) {
		return this.scannedRegions.contains(regionPos);
	}

	public boolean markRegionScanned(ChunkPos regionPos) {
		if (this.scannedRegions.add(regionPos)) {
			setDirty();
			return true;
		}
		return false;
	}

	public Set<ChunkPos> getScannedRegions() {
		return Collections.unmodifiableSet(this.scannedRegions);
	}

	public List<ChunkPos> getScannedRegionList() {
		return new ArrayList<>(this.scannedRegions);
	}

	public boolean invalidateNode(String nodeId) {
		if (this.nodes.remove(nodeId) != null) {
			setDirty();
			return true;
		}
		return false;
	}

	public void clear() {
		this.nodes.clear();
		this.edges.clear();
		this.routes.clear();
		this.physicalRoutes.clear();
		this.scannedRegions.clear();
		setDirty();
	}

	/**
	 * Manual serialization helper for testing and offline inspection.
	 */
	public CompoundTag serializeToNbt(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		DynamicOps<Tag> ops = registries != null ? registries.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		tag.put("data", CODEC.encodeStart(ops, this).getOrThrow());
		tag.putInt("DataVersion", this.dataVersion);
		return tag;
	}

	/**
	 * Manual deserialization helper for testing and offline inspection.
	 */
	public static RoadNetworkSavedData deserializeFromNbt(CompoundTag tag, HolderLookup.Provider registries) {
		DynamicOps<Tag> ops = registries != null ? registries.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		Tag dataTag = tag.get("data");
		if (dataTag == null) {
			return new RoadNetworkSavedData();
		}
		return CODEC.parse(ops, dataTag).getOrThrow();
	}
}

