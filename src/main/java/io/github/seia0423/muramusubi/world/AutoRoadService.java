package io.github.seia0423.muramusubi.world;

import io.github.seia0423.muramusubi.MuraMusubi;
import io.github.seia0423.muramusubi.config.MuraMusubiConfig;
import io.github.seia0423.muramusubi.persistence.RoadNetworkSavedData;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadConnection;
import io.github.seia0423.muramusubi.road.TerrainRoadPlanner;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 読み込まれた村を発見し、未接続の最寄り村への道路を自動計画します。 */
public final class AutoRoadService {
    private static final int CHUNKS_PER_TICK = 2;
    private static final Deque<PendingChunk> PENDING_CHUNKS = new ConcurrentLinkedDeque<>();
    private static final Set<PendingChunk> PENDING_CHUNK_KEYS = ConcurrentHashMap.newKeySet();
    private static final Deque<PendingRoadSearch> PENDING_SEARCHES = new ArrayDeque<>();
    private static final Set<PendingConnection> PENDING_CONNECTIONS = new HashSet<>();
    private static final Set<PendingConnection> SESSION_ATTEMPTS = new HashSet<>();
    private static final TerrainRoadPlanner PLANNER = new TerrainRoadPlanner();

    private AutoRoadService() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos chunkPos = event.getChunk().getPos();
        PendingChunk pending = new PendingChunk(level, chunkPos.x(), chunkPos.z());
        if (PENDING_CHUNK_KEYS.add(pending)) {
            PENDING_CHUNKS.addLast(pending);
        }
    }

    public static void tick(ServerTickEvent.Post event) {
        processLoadedChunks();
        if (!MuraMusubiConfig.AUTOMATIC_ROAD_GENERATION.getAsBoolean()) {
            PENDING_SEARCHES.clear();
            PENDING_CONNECTIONS.clear();
            SESSION_ATTEMPTS.clear();
            return;
        }
        advanceRoadSearch();
    }

    public static void clear(ServerStoppedEvent event) {
        PENDING_CHUNKS.clear();
        PENDING_CHUNK_KEYS.clear();
        PENDING_SEARCHES.clear();
        PENDING_CONNECTIONS.clear();
        SESSION_ATTEMPTS.clear();
    }

    private static void processLoadedChunks() {
        int remaining = CHUNKS_PER_TICK;
        while (remaining-- > 0 && !PENDING_CHUNKS.isEmpty()) {
            PendingChunk pending = PENDING_CHUNKS.removeFirst();
            PENDING_CHUNK_KEYS.remove(pending);
            discoverVillages(pending);
        }
    }

    private static void discoverVillages(PendingChunk pending) {
        ServerLevel level = pending.level();
        var structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var starts = level.structureManager().startsForStructure(
                new ChunkPos(pending.chunkX(), pending.chunkZ()),
                structure -> structureRegistry.get(structureRegistry.getId(structure))
                        .map(holder -> holder.is(StructureTags.VILLAGE))
                        .orElse(false));
        for (StructureStart start : starts) {
            if (!start.isValid()) {
                continue;
            }
            var center = start.getBoundingBox().getCenter();
            GridPoint village = new GridPoint(center.getX(), center.getZ())
                    .snappedTo(TerrainRoadPlanner.GRID_SIZE);
            RoadNetworkSavedData savedData = RoadNetworkSavedData.get(level);
            boolean discovered = savedData.rememberVillage(village);
            if (discovered) {
                MuraMusubi.LOGGER.info("村を自動発見しました: {}", village);
            }
            scheduleNearestConnection(level, village, savedData);
        }
    }

    private static void scheduleNearestConnection(ServerLevel level, GridPoint village,
            RoadNetworkSavedData savedData) {
        if (!MuraMusubiConfig.AUTOMATIC_ROAD_GENERATION.getAsBoolean()) {
            return;
        }
        long maximumDistance = MuraMusubiConfig.MAX_CONNECTION_DISTANCE.getAsInt();
        Optional<GridPoint> nearest = savedData.villages().stream()
                .filter(candidate -> !candidate.equals(village))
                .filter(candidate -> candidate.squaredDistanceTo(village) <= maximumDistance * maximumDistance)
                .filter(candidate -> {
                    RoadConnection connection = RoadConnection.between(village, candidate);
                    PendingConnection key = new PendingConnection(level, connection);
                    return !savedData.hasConnection(connection)
                            && !PENDING_CONNECTIONS.contains(key)
                            && !SESSION_ATTEMPTS.contains(key);
                })
                .min(Comparator.comparingLong(candidate -> candidate.squaredDistanceTo(village)));
        if (nearest.isEmpty()) {
            return;
        }

        RoadConnection connection = RoadConnection.between(village, nearest.get());
        PendingConnection key = new PendingConnection(level, connection);
        TerrainRoadPlanner.Search search = PLANNER.begin(
                connection.first(),
                connection.second(),
                new MinecraftTerrainSampler(level),
                MuraMusubiConfig.MAX_PATHFINDING_STEPS.getAsInt());
        PENDING_CONNECTIONS.add(key);
        SESSION_ATTEMPTS.add(key);
        PENDING_SEARCHES.addLast(new PendingRoadSearch(level, connection, search));
        MuraMusubi.LOGGER.info("村間道路の自動探索を開始しました: {}", connection);
    }

    private static void advanceRoadSearch() {
        PendingRoadSearch pending = PENDING_SEARCHES.peekFirst();
        if (pending == null) {
            return;
        }
        TerrainRoadPlanner.SearchStatus status = pending.search().advance(
                MuraMusubiConfig.AUTO_PATHFINDING_STEPS_PER_TICK.getAsInt());
        if (status == TerrainRoadPlanner.SearchStatus.RUNNING) {
            return;
        }

        PENDING_SEARCHES.removeFirst();
        PENDING_CONNECTIONS.remove(new PendingConnection(pending.level(), pending.connection()));
        if (status == TerrainRoadPlanner.SearchStatus.FOUND) {
            var enqueueResult = RoadBuildService.enqueue(
                    pending.level(),
                    pending.search().result().orElseThrow(),
                    MuraMusubiConfig.ROAD_WIDTH.getAsInt());
            MuraMusubi.LOGGER.info("村間道路を自動生成キューへ追加しました: {}（{}ブロック）",
                    pending.connection(), enqueueResult.blockCount());
        } else {
            MuraMusubi.LOGGER.warn("村間道路の自動探索に失敗しました: {}", pending.connection());
        }
    }

    public static int pendingSearchCount() {
        return PENDING_SEARCHES.size();
    }

    private record PendingChunk(ServerLevel level, int chunkX, int chunkZ) {
    }

    private record PendingRoadSearch(ServerLevel level, RoadConnection connection,
            TerrainRoadPlanner.Search search) {
    }

    private record PendingConnection(ServerLevel level, RoadConnection connection) {
    }
}
