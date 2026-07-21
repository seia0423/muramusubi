package io.github.seia0423.muramusubi.world;

import io.github.seia0423.muramusubi.MuraMusubi;
import io.github.seia0423.muramusubi.config.MuraMusubiConfig;
import io.github.seia0423.muramusubi.persistence.RoadBuildOperation;
import io.github.seia0423.muramusubi.persistence.RoadNetworkSavedData;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadConnection;
import io.github.seia0423.muramusubi.road.RoadPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 道路ブロックの変更をサーバーティックごとに少量ずつ実行します。 */
public final class RoadBuildService {
    private RoadBuildService() {
    }

    public static EnqueueResult enqueue(ServerLevel level, RoadPlan plan, int width) {
        RoadConnection connection = RoadConnection.between(plan.start(), plan.end());
        RoadNetworkSavedData savedData = RoadNetworkSavedData.get(level);
        if (savedData.hasConnection(connection)) {
            return new EnqueueResult(EnqueueStatus.DUPLICATE, 0);
        }

        RoadStyle style = selectStyle(connection);
        if (style == null) {
            return new EnqueueResult(EnqueueStatus.DISABLED, 0);
        }
        int palette = Math.floorMod(connection.hashCode() / 2, 3);
        int configuredClearance = MuraMusubiConfig.ENDPOINT_CLEARANCE.getAsInt();
        int usableClearance = Math.min(configuredClearance, Math.max(0, (plan.centerLine().size() - 1) / 4));
        int endIndex = plan.centerLine().size() - usableClearance;
        List<GridPoint> buildableCenterLine = plan.centerLine().subList(usableClearance, endIndex);
        List<RoadBuildOperation> operations = createOperations(buildableCenterLine, width, style, palette);
        if (!savedData.queueRoad(connection, operations)) {
            return new EnqueueResult(EnqueueStatus.DUPLICATE, 0);
        }
        return new EnqueueResult(EnqueueStatus.QUEUED, operations.size());
    }

    public static void tick(ServerTickEvent.Post event) {
        int budget = MuraMusubiConfig.MAX_BLOCKS_PER_TICK.getAsInt();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            RoadNetworkSavedData savedData = RoadNetworkSavedData.get(level);
            while (budget > 0) {
                var buildStep = savedData.pollNextBuildStep();
                if (buildStep.isEmpty()) {
                    break;
                }
                placeOperation(level, buildStep.get().operation());
                buildStep.get().completedConnection().ifPresent(connection ->
                        MuraMusubi.LOGGER.info("道路の敷設が完了しました: {}", connection));
                budget--;
            }
            if (budget == 0) {
                return;
            }
        }
    }

    private static List<RoadBuildOperation> createOperations(List<GridPoint> centerLine,
            int width, RoadStyle style, int palette) {
        List<RoadBuildOperation> operations = new ArrayList<>();
        RoadBuildOperation.Kind roadKind = style == RoadStyle.ARTIFICIAL
                ? RoadBuildOperation.Kind.ROAD_ARTIFICIAL
                : RoadBuildOperation.Kind.ROAD_NATURAL;
        for (GridPoint point : expandWidth(centerLine, width).values()) {
            operations.add(new RoadBuildOperation(point, roadKind, 0, palette));
        }
        if (MuraMusubiConfig.PLACE_ROAD_DECORATIONS.getAsBoolean()) {
            addDecorations(operations, centerLine, width, style);
        }
        return operations;
    }

    private static Map<Long, GridPoint> expandWidth(List<GridPoint> centerLine, int width) {
        int firstOffset = -(width / 2);
        int lastOffset = firstOffset + width - 1;
        Map<Long, GridPoint> unique = new LinkedHashMap<>();
        for (int index = 0; index < centerLine.size(); index++) {
            GridPoint center = centerLine.get(index);
            GridPoint previous = centerLine.get(Math.max(0, index - 1));
            GridPoint next = centerLine.get(Math.min(centerLine.size() - 1, index + 1));
            int dx = next.x() - previous.x();
            int dz = next.z() - previous.z();

            for (int offset = firstOffset; offset <= lastOffset; offset++) {
                if (dx != 0 && dz != 0) {
                    add(unique, center.x() + offset, center.z());
                    add(unique, center.x(), center.z() + offset);
                } else if (Math.abs(dx) >= Math.abs(dz)) {
                    add(unique, center.x(), center.z() + offset);
                } else {
                    add(unique, center.x() + offset, center.z());
                }
            }
        }
        return unique;
    }

    private static void addDecorations(List<RoadBuildOperation> operations,
            List<GridPoint> centerLine, int width, RoadStyle style) {
        int spacing = MuraMusubiConfig.DECORATION_SPACING.getAsInt();
        int sideDistance = width / 2 + 2;
        for (int index = spacing; index < centerLine.size() - spacing; index += spacing) {
            GridPoint previous = centerLine.get(index - 1);
            GridPoint next = centerLine.get(index + 1);
            int directionX = Integer.signum(next.x() - previous.x());
            int directionZ = Integer.signum(next.z() - previous.z());
            int normalX = -directionZ;
            int normalZ = directionX;
            int side = (index / spacing) % 2 == 0 ? 1 : -1;
            GridPoint center = centerLine.get(index);
            GridPoint base = new GridPoint(
                    center.x() + normalX * sideDistance * side,
                    center.z() + normalZ * sideDistance * side);

            if (style == RoadStyle.ARTIFICIAL) {
                GridPoint arm = new GridPoint(base.x() - normalX * side, base.z() - normalZ * side);
                operations.add(new RoadBuildOperation(base, RoadBuildOperation.Kind.FENCE, 1, 0));
                operations.add(new RoadBuildOperation(base, RoadBuildOperation.Kind.FENCE, 2, 0));
                operations.add(new RoadBuildOperation(base, RoadBuildOperation.Kind.FENCE, 3, 0));
                operations.add(new RoadBuildOperation(arm, RoadBuildOperation.Kind.FENCE, 3, 0));
                operations.add(new RoadBuildOperation(arm, RoadBuildOperation.Kind.HANGING_LANTERN, 2, 0));
            } else {
                operations.add(new RoadBuildOperation(base, RoadBuildOperation.Kind.FENCE, 1, 0));
                operations.add(new RoadBuildOperation(base, RoadBuildOperation.Kind.TORCH, 2, 0));
            }
        }
    }

    private static void placeOperation(ServerLevel level, RoadBuildOperation operation) {
        GridPoint point = operation.point();
        level.getChunk(point.x() >> 4, point.z() >> 4);
        int surfaceY = surfaceY(level, point);
        BlockPos surfacePos = new BlockPos(point.x(), surfaceY, point.z());
        switch (operation.kind()) {
            case ROAD_ARTIFICIAL, ROAD_NATURAL -> placeRoadSurface(level, surfacePos, operation);
            case FENCE -> placeFence(
                    level, surfacePos.above(operation.verticalOffset()), selectFence(level, surfacePos).defaultBlockState());
            case TORCH -> placeStandingDecoration(
                    level, surfacePos.above(operation.verticalOffset()), Blocks.TORCH.defaultBlockState());
            case HANGING_LANTERN -> placeHangingDecoration(
                    level,
                    surfacePos.above(operation.verticalOffset()),
                    Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        }
    }

    private static void placeRoadSurface(ServerLevel level, BlockPos surfacePos,
            RoadBuildOperation operation) {
        BlockState existing = level.getBlockState(surfacePos);
        if (!canReplaceRoadSurface(existing)) {
            return;
        }
        BlockState replacement = !existing.getFluidState().isEmpty()
                ? selectPlanks(level, surfacePos).defaultBlockState()
                : selectLandMaterial(operation).defaultBlockState();
        level.setBlock(surfacePos, replacement, Block.UPDATE_ALL);
        clearNaturalObstruction(level, surfacePos.above());
    }

    private static void placeFence(ServerLevel level, BlockPos target, BlockState state) {
        if (!canReplaceDecoration(level.getBlockState(target))) {
            return;
        }
        boolean supportedBelow = !level.getBlockState(target.below()).isAir();
        boolean supportedBeside = java.util.stream.Stream.of(
                        target.north(), target.south(), target.east(), target.west())
                .anyMatch(pos -> level.getBlockState(pos).is(BlockTags.FENCES));
        if (!supportedBelow && !supportedBeside) {
            return;
        }
        level.setBlock(target, state, Block.UPDATE_ALL);
    }

    private static void placeStandingDecoration(ServerLevel level, BlockPos target, BlockState state) {
        if (canReplaceDecoration(level.getBlockState(target))
                && !level.getBlockState(target.below()).isAir()) {
            level.setBlock(target, state, Block.UPDATE_ALL);
        }
    }

    private static void placeHangingDecoration(ServerLevel level, BlockPos target, BlockState state) {
        if (canReplaceDecoration(level.getBlockState(target))
                && !level.getBlockState(target.above()).isAir()) {
            level.setBlock(target, state, Block.UPDATE_ALL);
        }
    }

    private static boolean canReplaceDecoration(BlockState existing) {
        return existing.isAir() || existing.canBeReplaced() || existing.is(BlockTags.LEAVES);
    }

    private static boolean canReplaceRoadSurface(BlockState existing) {
        return !existing.is(Blocks.BEDROCK)
                && !existing.is(Blocks.PACKED_ICE)
                && !existing.is(Blocks.ICE)
                && !existing.is(Blocks.BLUE_ICE)
                && !existing.is(BlockTags.LEAVES)
                && !existing.is(BlockTags.LOGS)
                && !existing.is(BlockTags.FENCES)
                && !existing.is(BlockTags.PLANKS)
                && !existing.isAir();
    }

    private static Block selectLandMaterial(RoadBuildOperation operation) {
        int variant = Math.floorMod(operation.point().x() * 31 + operation.point().z() * 17, 6);
        if (operation.kind() == RoadBuildOperation.Kind.ROAD_ARTIFICIAL) {
            return switch (operation.palette()) {
                case 0 -> variant < 3 ? Blocks.MUD_BRICKS : Blocks.PACKED_MUD;
                case 1 -> variant < 3 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS;
                default -> switch (variant) {
                    case 0, 1, 2 -> Blocks.STONE_BRICKS;
                    case 3, 4 -> Blocks.MOSSY_STONE_BRICKS;
                    default -> Blocks.CRACKED_STONE_BRICKS;
                };
            };
        }
        return switch (operation.palette()) {
            case 0 -> switch (variant) {
                case 0, 1, 2 -> Blocks.COARSE_DIRT;
                case 3, 4 -> Blocks.ROOTED_DIRT;
                default -> Blocks.PACKED_MUD;
            };
            case 1 -> switch (variant) {
                case 0, 1, 2 -> Blocks.COBBLESTONE;
                case 3, 4 -> Blocks.MOSSY_COBBLESTONE;
                default -> Blocks.CRACKED_STONE_BRICKS;
            };
            default -> switch (variant) {
                case 0, 1, 2 -> Blocks.DIRT_PATH;
                case 3, 4 -> Blocks.COARSE_DIRT;
                default -> Blocks.PACKED_MUD;
            };
        };
    }

    private static Block selectFence(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return Blocks.JUNGLE_FENCE;
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return Blocks.ACACIA_FENCE;
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return Blocks.SPRUCE_FENCE;
        }
        return Blocks.OAK_FENCE;
    }

    private static Block selectPlanks(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return Blocks.JUNGLE_PLANKS;
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return Blocks.ACACIA_PLANKS;
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return Blocks.SPRUCE_PLANKS;
        }
        return Blocks.OAK_PLANKS;
    }

    private static int surfaceY(ServerLevel level, GridPoint point) {
        var chunkSource = level.getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(
                point.x(), point.z(), Heightmap.Types.WORLD_SURFACE_WG,
                level, chunkSource.randomState()) - 1;
    }

    private static RoadStyle selectStyle(RoadConnection connection) {
        boolean artificial = MuraMusubiConfig.ALLOW_ARTIFICIAL_ROADS.getAsBoolean();
        boolean natural = MuraMusubiConfig.ALLOW_NATURAL_ROADS.getAsBoolean();
        if (!artificial && !natural) {
            return null;
        }
        if (artificial && natural) {
            return (connection.hashCode() & 1) == 0 ? RoadStyle.ARTIFICIAL : RoadStyle.NATURAL;
        }
        return artificial ? RoadStyle.ARTIFICIAL : RoadStyle.NATURAL;
    }

    private static long hash(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static void add(Map<Long, GridPoint> positions, int x, int z) {
        positions.putIfAbsent(hash(x, z), new GridPoint(x, z));
    }

    private static void clearNaturalObstruction(ServerLevel level, BlockPos start) {
        for (int offset = 0; offset < 3; offset++) {
            BlockPos pos = start.above(offset);
            var state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.canBeReplaced() || state.is(BlockTags.LEAVES)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private enum RoadStyle {
        ARTIFICIAL,
        NATURAL
    }

    public enum EnqueueStatus {
        QUEUED,
        DUPLICATE,
        DISABLED
    }

    public record EnqueueResult(EnqueueStatus status, int blockCount) {
    }
}
