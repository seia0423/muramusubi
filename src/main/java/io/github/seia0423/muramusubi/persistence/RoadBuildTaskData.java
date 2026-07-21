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
            GridPoint.CODEC.listOf().fieldOf("remaining_positions")
                    .forGetter(RoadBuildTaskData::remainingPositions)
    ).apply(instance, RoadBuildTaskData::new));

    private final RoadConnection connection;
    private final Deque<GridPoint> remainingPositions;

    public RoadBuildTaskData(RoadConnection connection, List<GridPoint> remainingPositions) {
        this.connection = connection;
        this.remainingPositions = new ArrayDeque<>(remainingPositions);
    }

    public RoadConnection connection() {
        return connection;
    }

    public List<GridPoint> remainingPositions() {
        return List.copyOf(remainingPositions);
    }

    public GridPoint pollFirst() {
        return remainingPositions.pollFirst();
    }

    public boolean isComplete() {
        return remainingPositions.isEmpty();
    }

    public int remainingBlockCount() {
        return remainingPositions.size();
    }
}
