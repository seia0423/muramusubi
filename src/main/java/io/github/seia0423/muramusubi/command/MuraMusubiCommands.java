package io.github.seia0423.muramusubi.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.datafixers.util.Pair;
import io.github.seia0423.muramusubi.config.MuraMusubiConfig;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadPlan;
import io.github.seia0423.muramusubi.road.TerrainRoadPlanner;
import io.github.seia0423.muramusubi.world.MinecraftTerrainSampler;
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
                        .executes(context -> connectNearest(context.getSource()))));
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

    private static int showStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Mura Musubi: 最大接続距離=" + MuraMusubiConfig.MAX_CONNECTION_DISTANCE.getAsInt()
                        + ", 道幅=" + MuraMusubiConfig.ROAD_WIDTH.getAsInt()
                        + ", 1tick上限=" + MuraMusubiConfig.MAX_BLOCKS_PER_TICK.getAsInt()
                        + ", 敷設待ち=" + RoadBuildService.queuedBlocks()), false);
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
        source.sendSuccess(() -> Component.literal(
                "村を検出しました: [" + from.x() + ", " + from.z() + "] → ["
                        + to.x() + ", " + to.z() + "]"), false);
        return plan(source, from.x(), from.z(), to.x(), to.z(), true);
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

        int queued = RoadBuildService.enqueue(
                source.getLevel(), roadPlan, MuraMusubiConfig.ROAD_WIDTH.getAsInt());
        source.sendSuccess(() -> Component.literal(
                "道路を敷設キューへ追加しました: " + queued + " ブロック"), true);
        return queued;
    }
}
