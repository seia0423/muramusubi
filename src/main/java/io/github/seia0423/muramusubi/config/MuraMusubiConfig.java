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

    public static final ModConfigSpec.BooleanValue ALLOW_ARTIFICIAL_ROADS = BUILDER
            .comment("石・泥レンガ系の人工道を生成候補に含める")
            .define("allowArtificialRoads", true);

    public static final ModConfigSpec.BooleanValue ALLOW_NATURAL_ROADS = BUILDER
            .comment("土・丸石系の自然道を生成候補に含める")
            .define("allowNaturalRoads", true);

    public static final ModConfigSpec.BooleanValue PLACE_ROAD_DECORATIONS = BUILDER
            .comment("人工道に街灯、自然道に松明付き道標を設置する")
            .define("placeRoadDecorations", true);

    public static final ModConfigSpec.IntValue DECORATION_SPACING = BUILDER
            .comment("街灯・道標のおおよその間隔（中心線ブロック数）")
            .defineInRange("decorationSpacing", 59, 12, 256);

    public static final ModConfigSpec.BooleanValue SMOOTH_ROAD_SLOPES = BUILDER
            .comment("1ブロック段差に階段またはハーフブロックを置く")
            .define("smoothRoadSlopes", true);

    public static final ModConfigSpec.BooleanValue GRADE_ROAD_TERRAIN = BUILDER
            .comment("急な道路で切土・盛土を行い、中心線の高低差を緩和する")
            .define("gradeRoadTerrain", true);

    public static final ModConfigSpec.IntValue MAXIMUM_TERRAIN_ADJUSTMENT = BUILDER
            .comment("切土・盛土で地表を上下させる最大ブロック数")
            .defineInRange("maximumTerrainAdjustment", 3, 0, 8);

    public static final ModConfigSpec.IntValue MINIMUM_BRIDGE_SPAN = BUILDER
            .comment("橋として整形する連続水域の最小長")
            .defineInRange("minimumBridgeSpan", 3, 2, 64);

    public static final ModConfigSpec.IntValue BRIDGE_PILLAR_SPACING = BUILDER
            .comment("橋脚同士のおおよその間隔")
            .defineInRange("bridgePillarSpacing", 8, 2, 64);

    public static final ModConfigSpec.IntValue MAXIMUM_BRIDGE_PILLAR_DEPTH = BUILDER
            .comment("橋脚を設置する水深の上限")
            .defineInRange("maximumBridgePillarDepth", 24, 1, 128);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MuraMusubiConfig() {
    }
}
