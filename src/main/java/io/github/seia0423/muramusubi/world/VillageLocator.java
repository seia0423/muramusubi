package io.github.seia0423.muramusubi.world;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import io.github.seia0423.muramusubi.road.GridPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;

public final class VillageLocator {
    private static final int[][] PROBE_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    public Optional<Pair<GridPoint, GridPoint>> findNearestPair(
            ServerLevel level, BlockPos origin, int maximumDistance) {
        int searchRadiusChunks = Math.max(8, maximumDistance / 16);
        BlockPos first = level.findNearestMapStructure(
                StructureTags.VILLAGE, origin, searchRadiusChunks, false);
        if (first == null) {
            return Optional.empty();
        }

        int probeDistance = Math.max(128, maximumDistance / 2);
        int probeRadiusChunks = Math.max(8, maximumDistance / 32);
        List<BlockPos> candidates = new ArrayList<>();
        for (int[] direction : PROBE_DIRECTIONS) {
            BlockPos probe = first.offset(direction[0] * probeDistance, 0, direction[1] * probeDistance);
            BlockPos found = level.findNearestMapStructure(
                    StructureTags.VILLAGE, probe, probeRadiusChunks, false);
            if (found != null && !sameVillage(first, found)
                    && horizontalDistanceSquared(first, found) <= (long) maximumDistance * maximumDistance) {
                candidates.add(found);
            }
        }

        return candidates.stream()
                .min(Comparator.comparingLong(candidate -> horizontalDistanceSquared(first, candidate)))
                .map(second -> Pair.of(toGridPoint(first), toGridPoint(second)));
    }

    private static boolean sameVillage(BlockPos first, BlockPos second) {
        return horizontalDistanceSquared(first, second) < 32L * 32L;
    }

    private static long horizontalDistanceSquared(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static GridPoint toGridPoint(BlockPos pos) {
        return new GridPoint(pos.getX(), pos.getZ());
    }
}
