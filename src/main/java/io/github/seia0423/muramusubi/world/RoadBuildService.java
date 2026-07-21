package io.github.seia0423.muramusubi.world;

import io.github.seia0423.muramusubi.MuraMusubi;
import io.github.seia0423.muramusubi.config.MuraMusubiConfig;
import io.github.seia0423.muramusubi.road.GridPoint;
import io.github.seia0423.muramusubi.road.RoadPlan;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 道路ブロックの変更をサーバーティックごとに少量ずつ実行します。 */
public final class RoadBuildService {
    private static final Deque<RoadBuildTask> TASKS = new ArrayDeque<>();

    private RoadBuildService() {
    }

    public static int enqueue(ServerLevel level, RoadPlan plan, int width) {
        int configuredClearance = MuraMusubiConfig.ENDPOINT_CLEARANCE.getAsInt();
        int usableClearance = Math.min(configuredClearance, Math.max(0, (plan.centerLine().size() - 1) / 4));
        int endIndex = plan.centerLine().size() - usableClearance;
        List<GridPoint> buildableCenterLine = plan.centerLine().subList(usableClearance, endIndex);
        Deque<GridPoint> positions = expandWidth(buildableCenterLine, width);
        int blockCount = positions.size();
        TASKS.addLast(new RoadBuildTask(level, positions));
        return blockCount;
    }

    public static int queuedBlocks() {
        return TASKS.stream().mapToInt(task -> task.positions().size()).sum();
    }

    public static void tick(ServerTickEvent.Post event) {
        int budget = MuraMusubiConfig.MAX_BLOCKS_PER_TICK.getAsInt();
        while (budget > 0 && !TASKS.isEmpty()) {
            RoadBuildTask task = TASKS.getFirst();
            GridPoint point = task.positions().pollFirst();
            if (point != null) {
                placeRoadBlock(task.level(), point);
                budget--;
            }
            if (task.positions().isEmpty()) {
                TASKS.removeFirst();
                MuraMusubi.LOGGER.info("道路の敷設が完了しました");
            }
        }
    }

    public static void clear(ServerStoppedEvent event) {
        TASKS.clear();
    }

    private static Deque<GridPoint> expandWidth(List<GridPoint> centerLine, int width) {
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
        return new ArrayDeque<>(unique.values());
    }

    private static void placeRoadBlock(ServerLevel level, GridPoint point) {
        level.getChunk(point.x() >> 4, point.z() >> 4);
        var chunkSource = level.getChunkSource();
        int surfaceY = chunkSource.getGenerator().getBaseHeight(
                point.x(), point.z(), Heightmap.Types.WORLD_SURFACE_WG,
                level, chunkSource.randomState()) - 1;
        BlockPos surfacePos = new BlockPos(point.x(), surfaceY, point.z());
        var existing = level.getBlockState(surfacePos);
        if (existing.is(Blocks.BEDROCK)
                || existing.is(Blocks.PACKED_ICE)
                || existing.is(Blocks.ICE)
                || existing.is(Blocks.BLUE_ICE)
                || existing.is(BlockTags.LEAVES)
                || existing.is(BlockTags.LOGS)
                || existing.is(BlockTags.FENCES)
                || existing.is(BlockTags.PLANKS)
                || existing.isAir()) {
            return;
        }

        var replacement = !existing.getFluidState().isEmpty()
                ? Blocks.OAK_PLANKS.defaultBlockState()
                : selectLandMaterial(point).defaultBlockState();
        level.setBlock(surfacePos, replacement, Block.UPDATE_ALL);
        clearNaturalObstruction(level, surfacePos.above());
    }

    private static Block selectLandMaterial(GridPoint point) {
        int variant = Math.floorMod(point.x() * 31 + point.z() * 17, 10);
        if (variant == 0) {
            return Blocks.COBBLESTONE;
        }
        if (variant <= 2) {
            return Blocks.GRAVEL;
        }
        if (variant <= 4) {
            return Blocks.COARSE_DIRT;
        }
        return Blocks.DIRT_PATH;
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

    private record RoadBuildTask(ServerLevel level, Deque<GridPoint> positions) {
    }
}
