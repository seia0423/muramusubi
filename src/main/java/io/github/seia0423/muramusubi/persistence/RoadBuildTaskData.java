package io.github.seia0423.muramusubi.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadConnection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** 保存可能な道路敷設キュー1件分です。 */
public final class RoadBuildTaskData {
    public static final Codec<RoadBuildTaskData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RoadConnection.CODEC.fieldOf("connection").forGetter(RoadBuildTaskData::connection),
            RoadBuildOperation.CODEC.listOf().optionalFieldOf("remaining_operations", List.of())
                    .forGetter(RoadBuildTaskData::remainingOperations),
            GridPoint.CODEC.listOf().optionalFieldOf("remaining_positions", List.of())
                    .forGetter(task -> List.of())
    ).apply(instance, RoadBuildTaskData::decode));

    private final RoadConnection connection;
    private final Deque<RoadBuildOperation> remainingOperations;

    public RoadBuildTaskData(RoadConnection connection, List<RoadBuildOperation> remainingOperations) {
        this.connection = connection;
        this.remainingOperations = new ArrayDeque<>(remainingOperations);
    }

    private static RoadBuildTaskData decode(RoadConnection connection,
            List<RoadBuildOperation> operations, List<GridPoint> legacyPositions) {
        if (!operations.isEmpty()) {
            return new RoadBuildTaskData(connection, operations);
        }
        List<RoadBuildOperation> migrated = legacyPositions.stream()
                .map(point -> new RoadBuildOperation(
                        point, RoadBuildOperation.Kind.ROAD_ARTIFICIAL, 0, 1))
                .toList();
        return new RoadBuildTaskData(connection, migrated);
    }

    public RoadConnection connection() {
        return connection;
    }

    public List<RoadBuildOperation> remainingOperations() {
        return List.copyOf(remainingOperations);
    }

    public RoadBuildOperation pollFirst() {
        return remainingOperations.pollFirst();
    }

    public boolean isComplete() {
        return remainingOperations.isEmpty();
    }

    public int remainingBlockCount() {
        return remainingOperations.size();
    }
}
