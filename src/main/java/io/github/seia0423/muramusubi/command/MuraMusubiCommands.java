package io.github.seia0423.muramusubi.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.seia0423.muramusubi.config.MuraMusubiConfig;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadPlan;
import io.github.seia0423.muramusubi.road.StraightRoadPlanner;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class MuraMusubiCommands {
    private static final int MIN_COORDINATE = -30_000_000;
    private static final int MAX_COORDINATE = 30_000_000;
    private static final StraightRoadPlanner PLANNER = new StraightRoadPlanner();

    private MuraMusubiCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        var toZ = Commands.argument("toZ", coordinate())
                .executes(context -> plan(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "fromX"),
                        IntegerArgumentType.getInteger(context, "fromZ"),
                        IntegerArgumentType.getInteger(context, "toX"),
                        IntegerArgumentType.getInteger(context, "toZ")));
        var toX = Commands.argument("toX", coordinate()).then(toZ);
        var fromZ = Commands.argument("fromZ", coordinate()).then(toX);
        var fromX = Commands.argument("fromX", coordinate()).then(fromZ);

        event.getDispatcher().register(Commands.literal("muramusubi")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("plan")
                        .then(fromX)));
    }

    private static IntegerArgumentType coordinate() {
        return IntegerArgumentType.integer(MIN_COORDINATE, MAX_COORDINATE);
    }

    private static int showStatus(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Mura Musubi: 最大接続距離=" + MuraMusubiConfig.MAX_CONNECTION_DISTANCE.getAsInt()
                        + ", 道幅=" + MuraMusubiConfig.ROAD_WIDTH.getAsInt()
                        + ", 1tick上限=" + MuraMusubiConfig.MAX_BLOCKS_PER_TICK.getAsInt()), false);
        return 1;
    }

    private static int plan(net.minecraft.commands.CommandSourceStack source, int fromX, int fromZ, int toX, int toZ) {
        GridPoint start = new GridPoint(fromX, fromZ);
        GridPoint end = new GridPoint(toX, toZ);
        long maximumDistance = MuraMusubiConfig.MAX_CONNECTION_DISTANCE.getAsInt();
        if (start.squaredDistanceTo(end) > maximumDistance * maximumDistance) {
            source.sendFailure(Component.literal("接続距離が設定上限を超えています"));
            return 0;
        }

        RoadPlan roadPlan = PLANNER.plan(start, end);
        source.sendSuccess(() -> Component.literal(
                "道路候補を計算しました: " + roadPlan.lengthInBlocks() + " ブロック（プレビューのみ）"), false);
        return roadPlan.lengthInBlocks();
    }
}
