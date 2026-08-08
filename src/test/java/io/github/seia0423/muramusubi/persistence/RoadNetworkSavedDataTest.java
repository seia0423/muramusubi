package io.github.seia0423.muramusubi.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadConnection;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoadNetworkSavedDataTest {
    @Test
    void canonicalConnectionPreventsReverseDuplicate() {
        RoadNetworkSavedData data = new RoadNetworkSavedData();
        RoadConnection forward = RoadConnection.between(
                new GridPoint(0, 0), new GridPoint(100, 40));
        RoadConnection reverse = RoadConnection.between(
                new GridPoint(100, 40), new GridPoint(0, 0));

        assertTrue(data.queueRoad(forward, List.of(roadAt(4, 0))));
        assertFalse(data.queueRoad(reverse, List.of(roadAt(8, 0))));
        assertEquals(1, data.connections().size());
    }

    @Test
    void codecPreservesVillagesConnectionsAndRemainingQueue() {
        RoadNetworkSavedData original = new RoadNetworkSavedData();
        GridPoint first = new GridPoint(-24, 48);
        GridPoint second = new GridPoint(120, 96);
        RoadConnection connection = RoadConnection.between(first, second);
        original.rememberVillage(first);
        original.rememberVillage(second);
        original.queueRoad(connection, List.of(
                roadAt(0, 0),
                new RoadBuildOperation(new GridPoint(1, 0), RoadBuildOperation.Kind.TERRAIN_FILL, 1, 0),
                new RoadBuildOperation(new GridPoint(2, 0), RoadBuildOperation.Kind.BRIDGE_PILLAR, -4, 0)));
        original.pollNextBuildStep();

        var encoded = RoadNetworkSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        RoadNetworkSavedData restored = RoadNetworkSavedData.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(List.of(first, second), restored.villages());
        assertEquals(List.of(connection), restored.connections());
        assertEquals(2, restored.queuedBlockCount());
        assertEquals(1, restored.pendingRoadCount());
        assertEquals(
                RoadBuildOperation.Kind.TERRAIN_FILL,
                restored.buildTasks().getFirst().remainingOperations().getFirst().kind());
        RoadBuildOperation restoredPillar = restored.buildTasks().getFirst()
                .remainingOperations().getLast();
        assertEquals(RoadBuildOperation.Kind.BRIDGE_PILLAR, restoredPillar.kind());
        assertEquals(-4, restoredPillar.verticalOffset());
    }

    @Test
    void completedTaskLeavesConnectionAsDuplicateGuard() {
        RoadNetworkSavedData data = new RoadNetworkSavedData();
        RoadConnection connection = RoadConnection.between(
                new GridPoint(0, 0), new GridPoint(16, 0));
        data.queueRoad(connection, List.of(roadAt(8, 0)));

        var step = data.pollNextBuildStep();

        assertTrue(step.isPresent());
        assertEquals(connection, step.get().completedConnection().orElseThrow());
        assertEquals(0, data.pendingRoadCount());
        assertTrue(data.hasConnection(connection));
    }

    @Test
    void forgettingConnectionAlsoCancelsPendingTask() {
        RoadNetworkSavedData data = new RoadNetworkSavedData();
        RoadConnection connection = RoadConnection.between(
                new GridPoint(0, 0), new GridPoint(16, 0));
        data.queueRoad(connection, List.of(roadAt(8, 0), roadAt(9, 0)));

        assertTrue(data.forgetConnection(connection));
        assertFalse(data.hasConnection(connection));
        assertEquals(0, data.pendingRoadCount());
        assertEquals(0, data.queuedBlockCount());
    }

    @Test
    void legacyPositionQueueMigratesToArtificialRoadOperations() {
        var legacyJson = JsonParser.parseString("""
                {
                  "connection": {
                    "first": {"x": 0, "z": 0},
                    "second": {"x": 16, "z": 0}
                  },
                  "remaining_positions": [
                    {"x": 8, "z": 0}
                  ]
                }
                """);

        RoadBuildTaskData restored = RoadBuildTaskData.CODEC
                .parse(JsonOps.INSTANCE, legacyJson)
                .getOrThrow();
        RoadBuildOperation operation = restored.remainingOperations().getFirst();

        assertEquals(new GridPoint(8, 0), operation.point());
        assertEquals(RoadBuildOperation.Kind.ROAD_ARTIFICIAL, operation.kind());
        assertEquals(1, operation.palette());
    }

    private static RoadBuildOperation roadAt(int x, int z) {
        return new RoadBuildOperation(
                new GridPoint(x, z), RoadBuildOperation.Kind.ROAD_ARTIFICIAL, 0, 1);
    }
}
