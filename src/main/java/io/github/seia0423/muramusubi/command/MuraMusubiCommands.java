package io.github.seia0423.muramusubi.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.datafixers.util.Pair;
import io.github.seia0423.muramusubi.config.MuraMusubiConfig;
import io.github.seia0423.muramusubi.persistence.RoadNetworkSavedData;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.MinimumSpanningRoadPlanner;
import io.github.seia0423.muramusubi.road.RoadConnection;
import io.github.seia0423.muramusubi.road.RoadNetworkPlan;
import io.github.seia0423.muramusubi.road.RoadPlan;
import io.github.seia0423.muramusubi.road.TerrainRoadPlanner;
import io.github.seia0423.muramusubi.world.MinecraftTerrainSampler;
import io.github.seia0423.muramusubi.world.AutoRoadService;
import io.github.seia0423.muramusubi.world.RoadBuildService;
import io.github.seia0423.muramusubi.world.VillageLocator;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class MuraMusubiCommands {
    private static final int MIN_COORDINATE = -30_000_000;
    private static final int MAX_COORDINATE = 30_000_000;
    private static final TerrainRoadPlanner PLANNER = new TerrainRoadPlanner();
    private static final MinimumSpanningRoadPlanner NETWORK_PLANNER = new MinimumSpanningRoadPlanner();
    private static final VillageLocator VILLAGE_LOCATOR = new VillageLocator();

    private MuraMusubiCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("muramusubi")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("plan")
                        .then(coordinateArguments(false)))
                .then(Commands.literal("connect")
                        .then(coordinateArguments(true)))
                .then(Commands.literal("connect-nearest")
                        .executes(context -> connectNearest(context.getSource())))
                .then(Commands.literal("connect-network")
                        .executes(context -> connectNetwork(context.getSource())))
                .then(Commands.literal("roads")
                        .executes(context -> listRoads(context.getSource())))
                .then(Commands.literal("forget")
                        .then(forgetArguments())));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer>
            coordinateArguments(boolean build) {
        return Commands.argument("fromX", coordinate())
                .then(Commands.argument("fromZ", coordinate())
                        .then(Commands.argument("toX", coordinate())
                                .then(Commands.argument("toZ", coordinate())
                                        .executes(context -> plan(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "fromX"),
                                                IntegerArgumentType.getInteger(context, "fromZ"),
                                                IntegerArgumentType.getInteger(context, "toX"),
                                                IntegerArgumentType.getInteger(context, "toZ"),
                                                build)))));
    }

    private static IntegerArgumentType coordinate() {
        return IntegerArgumentType.integer(MIN_COORDINATE, MAX_COORDINATE);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer>
            forgetArguments() {
        return Commands.argument("fromX", coordinate())
                .then(Commands.argument("fromZ", coordinate())
                        .then(Commands.argument("toX", coordinate())
                                .then(Commands.argument("toZ", coordinate())
                                        .executes(context -> forgetConnection(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "fromX"),
                                                IntegerArgumentType.getInteger(context, "fromZ"),
                                                IntegerArgumentType.getInteger(context, "toX"),
                                                IntegerArgumentType.getInteger(context, "toZ"))))));
    }

    private static int showStatus(CommandSourceStack source) {
        RoadNetworkSavedData savedData = RoadNetworkSavedData.get(source.getLevel());
        source.sendSuccess(() -> Component.literal(
                "Mura Musubi: 最大接続距離=" + MuraMusubiConfig.MAX_CONNECTION_DISTANCE.getAsInt()
                        + ", 道幅=" + MuraMusubiConfig.ROAD_WIDTH.getAsInt()
                        + ", 人工道=" + MuraMusubiConfig.ALLOW_ARTIFICIAL_ROADS.getAsBoolean()
                        + ", 自然道=" + MuraMusubiConfig.ALLOW_NATURAL_ROADS.getAsBoolean()
                        + ", 装飾=" + MuraMusubiConfig.PLACE_ROAD_DECORATIONS.getAsBoolean()
                        + ", 斜面補正=" + MuraMusubiConfig.SMOOTH_ROAD_SLOPES.getAsBoolean()
                        + ", 最小橋長=" + MuraMusubiConfig.MINIMUM_BRIDGE_SPAN.getAsInt()
                        + ", 1tick上限=" + MuraMusubiConfig.MAX_BLOCKS_PER_TICK.getAsInt()
                        + ", 発見済み村=" + savedData.villages().size()
                        + ", 接続済み=" + savedData.connections().size()
                        + ", 自動探索中=" + AutoRoadService.pendingSearchCount()
                        + ", 生成中道路=" + savedData.pendingRoadCount()
                        + ", 敷設待ち=" + savedData.queuedBlockCount()), false);
        return 1;
    }

    private static int connectNearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        int maximumDistance = MuraMusubiConfig.MAX_CONNECTION_DISTANCE.getAsInt();
        Optional<Pair<GridPoint, GridPoint>> pair = VILLAGE_LOCATOR.findNearestPair(
                level, origin, maximumDistance);
        if (pair.isEmpty()) {
            source.sendFailure(Component.literal("接続可能な2つの村を範囲内で見つけられませんでした"));
            return 0;
        }

        GridPoint from = pair.get().getFirst();
        GridPoint to = pair.get().getSecond();
        RoadNetworkSavedData savedData = RoadNetworkSavedData.get(level);
        savedData.rememberVillage(from.snappedTo(TerrainRoadPlanner.GRID_SIZE));
        savedData.rememberVillage(to.snappedTo(TerrainRoadPlanner.GRID_SIZE));
        source.sendSuccess(() -> Component.literal(
                "村を検出しました: [" + from.x() + ", " + from.z() + "] → ["
                        + to.x() + ", " + to.z() + "]"), false);
        return plan(source, from.x(), from.z(), to.x(), to.z(), true);
    }

    private static int connectNetwork(CommandSourceStack source) {
        RoadNetworkSavedData savedData = RoadNetworkSavedData.get(source.getLevel());
        RoadNetworkPlan networkPlan = NETWORK_PLANNER.plan(
                savedData.villages(), MuraMusubiConfig.MAX_CONNECTION_DISTANCE.getAsInt());
        if (networkPlan.connections().isEmpty()) {
            source.sendFailure(Component.literal("保存済みの村が2つ以上ないか、接続距離内にありません"));
            return 0;
        }

        int roadLimit = MuraMusubiConfig.MAX_NETWORK_ROADS_PER_COMMAND.getAsInt();
        int queuedRoads = 0;
        int failedRoads = 0;
        int queuedBlocks = 0;
        MinecraftTerrainSampler terrainSampler = new MinecraftTerrainSampler(source.getLevel());
        for (RoadConnection connection : networkPlan.connections()) {
            if (queuedRoads >= roadLimit) {
                break;
            }
            if (savedData.hasConnection(connection)) {
                continue;
            }

            Optional<RoadPlan> result = PLANNER.plan(
                    connection.first(),
                    connection.second(),
                    terrainSampler,
                    MuraMusubiConfig.MAX_PATHFINDING_STEPS.getAsInt());
            if (result.isEmpty()) {
                failedRoads++;
                continue;
            }
            RoadBuildService.EnqueueResult enqueueResult = RoadBuildService.enqueue(
                    source.getLevel(), result.get(), MuraMusubiConfig.ROAD_WIDTH.getAsInt());
            if (enqueueResult.status() == RoadBuildService.EnqueueStatus.QUEUED) {
                queuedRoads++;
                queuedBlocks += enqueueResult.blockCount();
            }
        }

        int finalQueuedRoads = queuedRoads;
        int finalFailedRoads = failedRoads;
        int finalQueuedBlocks = queuedBlocks;
        source.sendSuccess(() -> Component.literal(
                "道路ネットワークを追加しました: " + finalQueuedRoads + "本 / "
                        + finalQueuedBlocks + "ブロック"
                        + (finalFailedRoads > 0 ? "（経路探索失敗 " + finalFailedRoads + "本）" : "")
                        + (networkPlan.isFullyConnected() ? "" : "（距離外の村群あり）")), true);
        return queuedRoads;
    }

    private static int listRoads(CommandSourceStack source) {
        RoadNetworkSavedData savedData = RoadNetworkSavedData.get(source.getLevel());
        source.sendSuccess(() -> Component.literal(
                "保存済み道路: " + savedData.connections().size()
                        + "本（生成中 " + savedData.pendingRoadCount() + "本）"), false);
        int displayCount = Math.min(10, savedData.connections().size());
        for (int index = 0; index < displayCount; index++) {
            RoadConnection connection = savedData.connections().get(index);
            source.sendSuccess(() -> Component.literal(formatConnection(connection)), false);
        }
        if (savedData.connections().size() > displayCount) {
            source.sendSuccess(() -> Component.literal(
                    "ほか " + (savedData.connections().size() - displayCount) + "本"), false);
        }
        return savedData.connections().size();
    }

    private static int forgetConnection(CommandSourceStack source, int fromX, int fromZ, int toX, int toZ) {
        RoadConnection connection = RoadConnection.between(
                new GridPoint(fromX, fromZ), new GridPoint(toX, toZ));
        if (!RoadNetworkSavedData.get(source.getLevel()).forgetConnection(connection)) {
            source.sendFailure(Component.literal("指定した接続記録は見つかりませんでした"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "接続記録と生成待ちを解除しました（設置済みブロックは残ります）: "
                        + formatConnection(connection)), true);
        return 1;
    }

    private static String formatConnection(RoadConnection connection) {
        return "[" + connection.first().x() + ", " + connection.first().z() + "] ↔ ["
                + connection.second().x() + ", " + connection.second().z() + "]";
    }

    private static int plan(CommandSourceStack source, int fromX, int fromZ,
            int toX, int toZ, boolean build) {
        GridPoint start = new GridPoint(fromX, fromZ);
        GridPoint end = new GridPoint(toX, toZ);
        long maximumDistance = MuraMusubiConfig.MAX_CONNECTION_DISTANCE.getAsInt();
        if (start.squaredDistanceTo(end) > maximumDistance * maximumDistance) {
            source.sendFailure(Component.literal("接続距離が設定上限を超えています"));
            return 0;
        }

        Optional<RoadPlan> result = PLANNER.plan(
                start,
                end,
                new MinecraftTerrainSampler(source.getLevel()),
                MuraMusubiConfig.MAX_PATHFINDING_STEPS.getAsInt());
        if (result.isEmpty()) {
            source.sendFailure(Component.literal("探索上限内で道路経路を見つけられませんでした"));
            return 0;
        }

        RoadPlan roadPlan = result.get();
        if (!build) {
            source.sendSuccess(() -> Component.literal(
                    "地形対応の道路候補: 中心線 " + roadPlan.lengthInBlocks() + " ブロック（プレビュー）"), false);
            return roadPlan.lengthInBlocks();
        }

        RoadBuildService.EnqueueResult enqueueResult = RoadBuildService.enqueue(
                source.getLevel(), roadPlan, MuraMusubiConfig.ROAD_WIDTH.getAsInt());
        if (enqueueResult.status() == RoadBuildService.EnqueueStatus.DUPLICATE) {
            source.sendFailure(Component.literal("この2地点は既に接続済み、または生成待ちです"));
            return 0;
        }
        if (enqueueResult.status() == RoadBuildService.EnqueueStatus.DISABLED) {
            source.sendFailure(Component.literal("設定で人工道と自然道が両方無効になっています"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "道路を永続化された敷設キューへ追加しました: "
                        + enqueueResult.blockCount() + " ブロック"), true);
        return enqueueResult.blockCount();
    }
}
