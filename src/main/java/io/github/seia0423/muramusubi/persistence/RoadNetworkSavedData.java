package io.github.seia0423.muramusubi.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.seia0423.muramusubi.MuraMusubi;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadConnection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** 村、接続済み道路、生成途中の位置をディメンションごとに保存します。 */
public final class RoadNetworkSavedData extends SavedData {
    public static final Codec<RoadNetworkSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GridPoint.CODEC.listOf().optionalFieldOf("villages", List.of())
                    .forGetter(RoadNetworkSavedData::villages),
            RoadConnection.CODEC.listOf().optionalFieldOf("connections", List.of())
                    .forGetter(RoadNetworkSavedData::connections),
            RoadBuildTaskData.CODEC.listOf().optionalFieldOf("build_tasks", List.of())
                    .forGetter(RoadNetworkSavedData::buildTasks)
    ).apply(instance, RoadNetworkSavedData::new));

    public static final SavedDataType<RoadNetworkSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(MuraMusubi.MOD_ID, "road_network"),
            RoadNetworkSavedData::new,
            CODEC);

    private final Set<GridPoint> villages;
    private final Set<RoadConnection> connections;
    private final List<RoadBuildTaskData> buildTasks;

    public RoadNetworkSavedData() {
        this(List.of(), List.of(), List.of());
    }

    private RoadNetworkSavedData(List<GridPoint> villages, List<RoadConnection> connections,
            List<RoadBuildTaskData> buildTasks) {
        this.villages = new LinkedHashSet<>(villages);
        this.connections = new LinkedHashSet<>(connections);
        this.buildTasks = new ArrayList<>(buildTasks);
    }

    public static RoadNetworkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean rememberVillage(GridPoint village) {
        boolean added = villages.add(village);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean queueRoad(RoadConnection connection, List<RoadBuildOperation> operations) {
        if (!connections.add(connection)) {
            return false;
        }
        buildTasks.add(new RoadBuildTaskData(connection, operations));
        setDirty();
        return true;
    }

    public boolean hasConnection(RoadConnection connection) {
        return connections.contains(connection);
    }

    public boolean forgetConnection(RoadConnection connection) {
        boolean removed = connections.remove(connection);
        boolean removedTask = buildTasks.removeIf(task -> task.connection().equals(connection));
        if (removed || removedTask) {
            setDirty();
        }
        return removed || removedTask;
    }

    public Optional<BuildStep> pollNextBuildStep() {
        while (!buildTasks.isEmpty()) {
            RoadBuildTaskData task = buildTasks.getFirst();
            RoadBuildOperation operation = task.pollFirst();
            if (operation == null) {
                buildTasks.removeFirst();
                setDirty();
                continue;
            }

            RoadConnection completedConnection = null;
            if (task.isComplete()) {
                completedConnection = task.connection();
                buildTasks.removeFirst();
            }
            setDirty();
            return Optional.of(new BuildStep(operation, Optional.ofNullable(completedConnection)));
        }
        return Optional.empty();
    }

    public List<GridPoint> villages() {
        return List.copyOf(villages);
    }

    public List<RoadConnection> connections() {
        return List.copyOf(connections);
    }

    public List<RoadBuildTaskData> buildTasks() {
        return List.copyOf(buildTasks);
    }

    public int queuedBlockCount() {
        return buildTasks.stream().mapToInt(RoadBuildTaskData::remainingBlockCount).sum();
    }

    public int pendingRoadCount() {
        return buildTasks.size();
    }

    public record BuildStep(RoadBuildOperation operation, Optional<RoadConnection> completedConnection) {
    }
}
