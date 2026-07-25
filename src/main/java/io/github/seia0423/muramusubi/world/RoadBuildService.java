package io.github.seia0423.muramusubi.world;

import io.github.seia0423.muramusubi.MuraMusubi;
import io.github.seia0423.muramusubi.config.MuraMusubiConfig;
import io.github.seia0423.muramusubi.persistence.RoadBuildOperation;
import io.github.seia0423.muramusubi.persistence.RoadNetworkSavedData;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadConnection;
import io.github.seia0423.muramusubi.road.RoadPlan;
import io.github.seia0423.muramusubi.road.RoadTerrainDesign;
import io.github.seia0423.muramusubi.road.TerrainSample;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        List<RoadBuildOperation> operations = createOperations(level, buildableCenterLine, width, style, palette);
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

    private static List<RoadBuildOperation> createOperations(ServerLevel level,
            List<GridPoint> centerLine, int width, RoadStyle style, int palette) {
        MinecraftTerrainSampler terrainSampler = new MinecraftTerrainSampler(level);
        List<TerrainSample> centerSamples = centerLine.stream()
                .map(point -> terrainSampler.sample(point.x(), point.z()))
                .toList();
        List<RoadTerrainDesign.BridgeSpan> bridgeSpans = RoadTerrainDesign.bridgeSpans(
                centerSamples, MuraMusubiConfig.MINIMUM_BRIDGE_SPAN.getAsInt());
        boolean gradeTerrain = MuraMusubiConfig.GRADE_ROAD_TERRAIN.getAsBoolean();
        List<Integer> roadHeights = gradeTerrain
                ? RoadTerrainDesign.gradeSurfaceHeights(
                        centerSamples, MuraMusubiConfig.MAXIMUM_TERRAIN_ADJUSTMENT.getAsInt())
                : centerSamples.stream().map(TerrainSample::surfaceY).toList();
        Set<Integer> bridgeIndexes = new HashSet<>();
        int[] bridgeDeckY = new int[centerLine.size()];
        for (RoadTerrainDesign.BridgeSpan span : bridgeSpans) {
            int heightSum = 0;
            for (int index = span.firstIndex(); index <= span.lastIndex(); index++) {
                bridgeIndexes.add(index);
                heightSum += centerSamples.get(index).surfaceY();
            }
            int averageHeight = Math.round((float) heightSum / span.length());
            for (int index = span.firstIndex(); index <= span.lastIndex(); index++) {
                bridgeDeckY[index] = averageHeight;
            }
        }

        List<RoadBuildOperation> operations = new ArrayList<>();
        RoadBuildOperation.Kind roadKind = style == RoadStyle.ARTIFICIAL
                ? RoadBuildOperation.Kind.ROAD_ARTIFICIAL
                : RoadBuildOperation.Kind.ROAD_NATURAL;
        Map<Long, RoadBuildOperation> surfaceOperations = new LinkedHashMap<>();
        for (int index = 0; index < centerLine.size(); index++) {
            boolean bridge = bridgeIndexes.contains(index);
            int surfaceWidth = bridge ? width + 2 : width;
            for (GridPoint point : expandAt(centerLine, index, surfaceWidth).values()) {
                RoadBuildOperation operation;
                if (bridge) {
                    int localSurfaceY = terrainSampler.sample(point.x(), point.z()).surfaceY();
                    operation = new RoadBuildOperation(
                            point, RoadBuildOperation.Kind.BRIDGE_DECK,
                            bridgeDeckY[index] - localSurfaceY, palette);
                    surfaceOperations.put(hash(point.x(), point.z()), operation);
                } else {
                    int localSurfaceY = terrainSampler.sample(point.x(), point.z()).surfaceY();
                    int maximumAdjustment = MuraMusubiConfig.MAXIMUM_TERRAIN_ADJUSTMENT.getAsInt();
                    int targetOffset = gradeTerrain
                            ? Math.clamp(
                                    roadHeights.get(index) - localSurfaceY,
                                    -maximumAdjustment,
                                    maximumAdjustment)
                            : 0;
                    operation = new RoadBuildOperation(point, roadKind, targetOffset, palette);
                    surfaceOperations.putIfAbsent(hash(point.x(), point.z()), operation);
                }
            }
        }
        if (gradeTerrain) {
            operations.addAll(createTerrainPreparation(surfaceOperations.values()));
        }
        operations.addAll(surfaceOperations.values());

        if (MuraMusubiConfig.SMOOTH_ROAD_SLOPES.getAsBoolean()) {
            addSlopeOperations(
                    operations, centerLine, roadHeights, bridgeIndexes,
                    terrainSampler, surfaceOperations, width, style, palette);
        }
        addBridgeStructures(
                operations, level, centerLine, bridgeSpans,
                bridgeDeckY, terrainSampler,
                new HashSet<>(surfaceOperations.keySet()), width);
        if (MuraMusubiConfig.PLACE_ROAD_DECORATIONS.getAsBoolean()) {
            addDecorations(operations, centerLine, bridgeIndexes, width, style);
        }
        return operations;
    }

    private static List<RoadBuildOperation> createTerrainPreparation(
            Iterable<RoadBuildOperation> surfaceOperations) {
        Map<BlockPos, RoadBuildOperation> preparation = new LinkedHashMap<>();
        for (RoadBuildOperation surface : surfaceOperations) {
            if (surface.kind() != RoadBuildOperation.Kind.ROAD_ARTIFICIAL
                    && surface.kind() != RoadBuildOperation.Kind.ROAD_NATURAL) {
                continue;
            }
            if (surface.verticalOffset() > 1) {
                for (int offset = 1; offset < surface.verticalOffset(); offset++) {
                    preparation.putIfAbsent(
                            new BlockPos(surface.point().x(), offset, surface.point().z()),
                            new RoadBuildOperation(
                                    surface.point(), RoadBuildOperation.Kind.TERRAIN_FILL,
                                    offset, surface.palette()));
                }
            } else if (surface.verticalOffset() < 0) {
                for (int offset = surface.verticalOffset() + 1; offset <= 0; offset++) {
                    preparation.putIfAbsent(
                            new BlockPos(surface.point().x(), offset, surface.point().z()),
                            new RoadBuildOperation(
                                    surface.point(), RoadBuildOperation.Kind.TERRAIN_CLEAR,
                                    offset, 0));
                }
            }
        }
        return List.copyOf(preparation.values());
    }

    private static Map<Long, GridPoint> expandAt(List<GridPoint> centerLine, int index, int width) {
        int firstOffset = -(width / 2);
        int lastOffset = firstOffset + width - 1;
        Map<Long, GridPoint> unique = new LinkedHashMap<>();
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
        return unique;
    }

    private static void addSlopeOperations(List<RoadBuildOperation> operations,
            List<GridPoint> centerLine, List<Integer> roadHeights,
            Set<Integer> bridgeIndexes, MinecraftTerrainSampler terrainSampler,
            Map<Long, RoadBuildOperation> surfaceOperations,
            int width, RoadStyle style, int palette) {
        Map<Long, RoadBuildOperation> slopeOperations = new LinkedHashMap<>();
        for (RoadTerrainDesign.SlopeTransition transition
                : RoadTerrainDesign.slopeTransitionsFromHeights(roadHeights)) {
            if (bridgeIndexes.contains(transition.lowerIndex())
                    || bridgeIndexes.contains(transition.higherIndex())) {
                continue;
            }
            GridPoint lower = centerLine.get(transition.lowerIndex());
            GridPoint higher = centerLine.get(transition.higherIndex());
            int stepX = Integer.signum(higher.x() - lower.x());
            int stepZ = Integer.signum(higher.z() - lower.z());
            RoadTerrainDesign.SlopeDirection direction = RoadTerrainDesign.slopeDirection(lower, higher);
            for (GridPoint point : expandAt(centerLine, transition.lowerIndex(), width).values()) {
                GridPoint higherPoint = new GridPoint(point.x() + stepX, point.z() + stepZ);
                RoadBuildOperation lowerRoad = surfaceOperations.get(hash(point.x(), point.z()));
                RoadBuildOperation higherRoad = surfaceOperations.get(hash(higherPoint.x(), higherPoint.z()));
                if (lowerRoad == null || higherRoad == null
                        || lowerRoad.kind() == RoadBuildOperation.Kind.BRIDGE_DECK
                        || higherRoad.kind() == RoadBuildOperation.Kind.BRIDGE_DECK) {
                    continue;
                }
                int lowerY = terrainSampler.sample(point.x(), point.z()).surfaceY()
                        + lowerRoad.verticalOffset();
                int higherY = terrainSampler.sample(higherPoint.x(), higherPoint.z()).surfaceY()
                        + higherRoad.verticalOffset();
                if (higherY - lowerY != 1) {
                    continue;
                }
                RoadBuildOperation operation = style == RoadStyle.ARTIFICIAL
                        && direction != RoadTerrainDesign.SlopeDirection.DIAGONAL
                        ? new RoadBuildOperation(
                                point, RoadBuildOperation.Kind.SLOPE_STAIR,
                                lowerRoad.verticalOffset() + 1, palette * 4 + direction.code())
                        : style == RoadStyle.ARTIFICIAL
                        ? new RoadBuildOperation(
                                point, RoadBuildOperation.Kind.SLOPE_ARTIFICIAL_SLAB,
                                lowerRoad.verticalOffset() + 1, palette)
                        : new RoadBuildOperation(
                                point, RoadBuildOperation.Kind.SLOPE_SLAB,
                                lowerRoad.verticalOffset() + 1, palette);
                slopeOperations.putIfAbsent(hash(point.x(), point.z()), operation);
            }
        }
        operations.addAll(slopeOperations.values());
    }

    private static void addBridgeStructures(List<RoadBuildOperation> operations,
            ServerLevel level, List<GridPoint> centerLine,
            List<RoadTerrainDesign.BridgeSpan> spans, int[] bridgeDeckY,
            MinecraftTerrainSampler terrainSampler, Set<Long> deckPositions,
            int roadWidth) {
        Map<Long, RoadBuildOperation> connectorDecks = new LinkedHashMap<>();
        Map<Long, RoadBuildOperation> rails = new LinkedHashMap<>();
        List<RoadBuildOperation> entranceDecorations = new ArrayList<>();
        Set<BlockPos> pillarBlocks = new HashSet<>();
        int bridgeWidth = roadWidth + 2;
        int firstSideOffset = -(bridgeWidth / 2);
        int lastSideOffset = firstSideOffset + bridgeWidth - 1;
        int pillarSpacing = MuraMusubiConfig.BRIDGE_PILLAR_SPACING.getAsInt();
        int maximumDepth = MuraMusubiConfig.MAXIMUM_BRIDGE_PILLAR_DEPTH.getAsInt();

        for (RoadTerrainDesign.BridgeSpan span : spans) {
            for (int index = span.firstIndex(); index <= span.lastIndex(); index++) {
                GridPoint center = centerLine.get(index);
                int[] normal = sideNormal(centerLine, index);
                for (int sideOffset : new int[] {firstSideOffset, lastSideOffset}) {
                    GridPoint side = new GridPoint(
                            center.x() + normal[0] * sideOffset,
                            center.z() + normal[1] * sideOffset);
                    List<GridPoint> railSegment = List.of(side);
                    if (index > span.firstIndex()) {
                        GridPoint previousCenter = centerLine.get(index - 1);
                        int[] previousNormal = sideNormal(centerLine, index - 1);
                        GridPoint previousSide = new GridPoint(
                                previousCenter.x() + previousNormal[0] * sideOffset,
                                previousCenter.z() + previousNormal[1] * sideOffset);
                        railSegment = orthogonalSegment(previousSide, side);
                    }
                    for (GridPoint railPoint : railSegment) {
                        int railSurfaceY = terrainSampler.sample(railPoint.x(), railPoint.z()).surfaceY();
                        long railKey = hash(railPoint.x(), railPoint.z());
                        if (deckPositions.add(railKey)) {
                            connectorDecks.put(
                                    railKey,
                                    new RoadBuildOperation(
                                            railPoint, RoadBuildOperation.Kind.BRIDGE_DECK,
                                            bridgeDeckY[index] - railSurfaceY, 0));
                        }
                        rails.putIfAbsent(
                                railKey,
                                new RoadBuildOperation(
                                        railPoint, RoadBuildOperation.Kind.BRIDGE_RAIL,
                                        bridgeDeckY[index] + 1 - railSurfaceY, 0));
                    }

                    boolean pillarIndex = (index - span.firstIndex()) % pillarSpacing == 0
                            || index == span.lastIndex();
                    if (!pillarIndex) {
                        continue;
                    }
                    int floorY = oceanFloorY(level, side);
                    int depth = bridgeDeckY[index] - floorY - 1;
                    if (depth < 1 || depth > maximumDepth) {
                        continue;
                    }
                    for (int y = floorY + 1; y < bridgeDeckY[index]; y++) {
                        pillarBlocks.add(new BlockPos(side.x(), y, side.z()));
                    }
                }
            }
            for (int entranceIndex : new int[] {span.firstIndex(), span.lastIndex()}) {
                GridPoint center = centerLine.get(entranceIndex);
                int[] normal = sideNormal(centerLine, entranceIndex);
                for (int sideOffset : new int[] {firstSideOffset, lastSideOffset}) {
                    GridPoint side = new GridPoint(
                            center.x() + normal[0] * sideOffset,
                            center.z() + normal[1] * sideOffset);
                    int localSurfaceY = terrainSampler.sample(side.x(), side.z()).surfaceY();
                    entranceDecorations.add(new RoadBuildOperation(
                            side, RoadBuildOperation.Kind.BRIDGE_RAIL,
                            bridgeDeckY[entranceIndex] + 2 - localSurfaceY, 0));
                    entranceDecorations.add(new RoadBuildOperation(
                            side, RoadBuildOperation.Kind.LANTERN,
                            bridgeDeckY[entranceIndex] + 3 - localSurfaceY, 0));
                }
            }
        }

        operations.addAll(connectorDecks.values());
        operations.addAll(rails.values());
        operations.addAll(entranceDecorations);
        for (BlockPos pillar : pillarBlocks) {
            int localSurfaceY = terrainSampler.sample(pillar.getX(), pillar.getZ()).surfaceY();
            operations.add(new RoadBuildOperation(
                    new GridPoint(pillar.getX(), pillar.getZ()),
                    RoadBuildOperation.Kind.BRIDGE_PILLAR,
                    pillar.getY() - localSurfaceY, 0));
        }
    }

    private static void addDecorations(List<RoadBuildOperation> operations,
            List<GridPoint> centerLine, Set<Integer> bridgeIndexes,
            int width, RoadStyle style) {
        int spacing = MuraMusubiConfig.DECORATION_SPACING.getAsInt();
        int sideDistance = width / 2 + 2;
        for (int index = spacing; index < centerLine.size() - spacing; index += spacing) {
            if (bridgeIndexes.contains(index)) {
                continue;
            }
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
            case ROAD_ARTIFICIAL, ROAD_NATURAL -> placeRoadSurface(
                    level, surfacePos.above(operation.verticalOffset()), operation);
            case SLOPE_STAIR -> placeSlopeBlock(
                    level,
                    surfacePos.above(operation.verticalOffset()),
                    selectArtificialStair(operation.palette() / 4).defaultBlockState()
                            .setValue(
                                    BlockStateProperties.HORIZONTAL_FACING,
                                    directionFromCode(operation.palette())));
            case SLOPE_SLAB -> placeSlopeBlock(
                    level,
                    surfacePos.above(operation.verticalOffset()),
                    selectNaturalSlab(operation.palette()).defaultBlockState());
            case SLOPE_ARTIFICIAL_SLAB -> placeSlopeBlock(
                    level,
                    surfacePos.above(operation.verticalOffset()),
                    selectArtificialSlab(operation.palette()).defaultBlockState());
            case TERRAIN_FILL -> placeTerrainFill(
                    level,
                    surfacePos.above(operation.verticalOffset()),
                    selectTerrainFill(operation.palette()).defaultBlockState());
            case TERRAIN_CLEAR -> clearTerrain(
                    level, surfacePos.above(operation.verticalOffset()));
            case BRIDGE_DECK -> placeBridgeDeck(
                    level, surfacePos.above(operation.verticalOffset()));
            case BRIDGE_RAIL -> placeFence(
                    level,
                    surfacePos.above(operation.verticalOffset()),
                    selectFence(level, surfacePos).defaultBlockState());
            case BRIDGE_PILLAR -> placeBridgePillar(
                    level,
                    surfacePos.above(operation.verticalOffset()),
                    selectBridgeSupport(level, surfacePos).defaultBlockState());
            case FENCE -> placeFence(
                    level, surfacePos.above(operation.verticalOffset()), selectFence(level, surfacePos).defaultBlockState());
            case TORCH -> placeStandingDecoration(
                    level, surfacePos.above(operation.verticalOffset()), Blocks.TORCH.defaultBlockState());
            case LANTERN -> placeStandingDecoration(
                    level, surfacePos.above(operation.verticalOffset()), Blocks.LANTERN.defaultBlockState());
            case HANGING_LANTERN -> placeHangingDecoration(
                    level,
                    surfacePos.above(operation.verticalOffset()),
                    Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        }
    }

    private static void placeRoadSurface(ServerLevel level, BlockPos surfacePos,
            RoadBuildOperation operation) {
        BlockState existing = level.getBlockState(surfacePos);
        boolean raisedRoadTarget = operation.verticalOffset() > 0
                && (existing.isAir() || existing.canBeReplaced() || !existing.getFluidState().isEmpty());
        if (!raisedRoadTarget && !canReplaceRoadSurface(existing)) {
            return;
        }
        BlockState replacement = !existing.getFluidState().isEmpty()
                ? selectPlanks(level, surfacePos).defaultBlockState()
                : selectLandMaterial(operation).defaultBlockState();
        level.setBlock(surfacePos, replacement, Block.UPDATE_ALL);
        clearNaturalObstruction(level, surfacePos.above());
    }

    private static void placeTerrainFill(ServerLevel level, BlockPos target, BlockState state) {
        BlockState existing = level.getBlockState(target);
        if (existing.isAir() || existing.canBeReplaced() || !existing.getFluidState().isEmpty()) {
            level.setBlock(target, state, Block.UPDATE_ALL);
        }
    }

    private static void clearTerrain(ServerLevel level, BlockPos target) {
        BlockState existing = level.getBlockState(target);
        if (canClearTerrain(existing)) {
            level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void placeSlopeBlock(ServerLevel level, BlockPos target, BlockState state) {
        BlockState existing = level.getBlockState(target);
        if (canReplaceDecoration(existing)
                || existing.is(BlockTags.STAIRS)
                || existing.is(BlockTags.SLABS)) {
            level.setBlock(target, state, Block.UPDATE_ALL);
        }
    }

    private static void placeBridgeDeck(ServerLevel level, BlockPos target) {
        BlockState existing = level.getBlockState(target);
        if (!canReplaceBridgeDeck(existing)) {
            return;
        }
        level.setBlock(target, selectPlanks(level, target).defaultBlockState(), Block.UPDATE_ALL);
        clearNaturalObstruction(level, target.above());
    }

    private static void placeBridgePillar(ServerLevel level, BlockPos target, BlockState state) {
        BlockState existing = level.getBlockState(target);
        if (existing.isAir() || !existing.getFluidState().isEmpty() || existing.canBeReplaced()) {
            level.setBlock(target, state, Block.UPDATE_ALL);
        }
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

    private static boolean canReplaceBridgeDeck(BlockState existing) {
        return !existing.is(Blocks.BEDROCK)
                && !existing.is(Blocks.PACKED_ICE)
                && !existing.is(Blocks.ICE)
                && !existing.is(Blocks.BLUE_ICE)
                && !existing.is(BlockTags.LOGS)
                && !existing.is(BlockTags.FENCES)
                && !existing.is(BlockTags.PLANKS);
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

    private static boolean canClearTerrain(BlockState existing) {
        return !existing.isAir()
                && !existing.hasBlockEntity()
                && !existing.is(Blocks.BEDROCK)
                && !existing.is(Blocks.PACKED_ICE)
                && !existing.is(Blocks.ICE)
                && !existing.is(Blocks.BLUE_ICE)
                && !existing.is(BlockTags.LOGS)
                && !existing.is(BlockTags.FENCES)
                && !existing.is(BlockTags.PLANKS);
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

    private static Block selectArtificialStair(int palette) {
        return switch (Math.floorMod(palette, 3)) {
            case 0 -> Blocks.MUD_BRICK_STAIRS;
            case 1 -> Blocks.POLISHED_ANDESITE_STAIRS;
            default -> Blocks.STONE_BRICK_STAIRS;
        };
    }

    private static Block selectArtificialSlab(int palette) {
        return switch (Math.floorMod(palette, 3)) {
            case 0 -> Blocks.MUD_BRICK_SLAB;
            case 1 -> Blocks.POLISHED_ANDESITE_SLAB;
            default -> Blocks.STONE_BRICK_SLAB;
        };
    }

    private static Block selectNaturalSlab(int palette) {
        return switch (Math.floorMod(palette, 3)) {
            case 0 -> Blocks.MUD_BRICK_SLAB;
            case 1 -> Blocks.COBBLESTONE_SLAB;
            default -> Blocks.STONE_SLAB;
        };
    }

    private static Block selectTerrainFill(int palette) {
        return switch (Math.floorMod(palette, 3)) {
            case 0 -> Blocks.DIRT;
            case 1 -> Blocks.ANDESITE;
            default -> Blocks.STONE;
        };
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

    private static Block selectBridgeSupport(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_JUNGLE)) {
            return Blocks.JUNGLE_LOG;
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return Blocks.ACACIA_LOG;
        }
        if (biome.is(BiomeTags.IS_TAIGA)) {
            return Blocks.SPRUCE_LOG;
        }
        return Blocks.OAK_LOG;
    }

    private static int surfaceY(ServerLevel level, GridPoint point) {
        var chunkSource = level.getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(
                point.x(), point.z(), Heightmap.Types.WORLD_SURFACE_WG,
                level, chunkSource.randomState()) - 1;
    }

    private static int oceanFloorY(ServerLevel level, GridPoint point) {
        var chunkSource = level.getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(
                point.x(), point.z(), Heightmap.Types.OCEAN_FLOOR_WG,
                level, chunkSource.randomState()) - 1;
    }

    private static Direction directionFromCode(int encodedPalette) {
        return switch (Math.floorMod(encodedPalette, 4)) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    private static int[] sideNormal(List<GridPoint> centerLine, int index) {
        GridPoint previous = centerLine.get(Math.max(0, index - 1));
        GridPoint next = centerLine.get(Math.min(centerLine.size() - 1, index + 1));
        int dx = next.x() - previous.x();
        int dz = next.z() - previous.z();
        return Math.abs(dx) >= Math.abs(dz) ? new int[] {0, 1} : new int[] {1, 0};
    }

    private static List<GridPoint> orthogonalSegment(GridPoint start, GridPoint end) {
        List<GridPoint> points = new ArrayList<>();
        int x = start.x();
        int z = start.z();
        points.add(start);
        while (x != end.x()) {
            x += Integer.signum(end.x() - x);
            points.add(new GridPoint(x, z));
        }
        while (z != end.z()) {
            z += Integer.signum(end.z() - z);
            points.add(new GridPoint(x, z));
        }
        return points;
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
