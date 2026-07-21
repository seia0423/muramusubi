package io.github.seia0423.muramusubi.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MuraMusubiConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_CONNECTION_DISTANCE = BUILDER
            .comment("村同士を接続候補にする最大距離（ブロック）")
            .defineInRange("maxConnectionDistance", 2048, 64, 16384);

    public static final ModConfigSpec.IntValue ROAD_WIDTH = BUILDER
            .comment("生成する道路の幅（ブロック）")
            .defineInRange("roadWidth", 3, 1, 15);

    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_TICK = BUILDER
            .comment("1ティックに変更する道路ブロック数の上限")
            .defineInRange("maxBlocksPerTick", 128, 1, 4096);

    public static final ModConfigSpec.IntValue MAX_PATHFINDING_STEPS = BUILDER
            .comment("道路1本のA*経路探索で調べる地点数の上限")
            .defineInRange("maxPathfindingSteps", 50_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue ENDPOINT_CLEARANCE = BUILDER
            .comment("村の建物を壊さないため、道路の始点・終点から敷設しない距離")
            .defineInRange("endpointClearance", 24, 0, 128);

    public static final ModConfigSpec.IntValue MAX_NETWORK_ROADS_PER_COMMAND = BUILDER
            .comment("connect-networkを1回実行したときに新しく計画する道路本数の上限")
            .defineInRange("maxNetworkRoadsPerCommand", 4, 1, 32);

    public static final ModConfigSpec.BooleanValue AUTOMATIC_ROAD_GENERATION = BUILDER
            .comment("読み込まれた村を自動発見し、最寄りの未接続村へ道路を生成する")
            .define("automaticRoadGeneration", true);

    public static final ModConfigSpec.IntValue AUTO_PATHFINDING_STEPS_PER_TICK = BUILDER
            .comment("自動道路のA*探索を1ティックに進める地点数")
            .defineInRange("autoPathfindingStepsPerTick", 256, 1, 10_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MuraMusubiConfig() {
    }
}
