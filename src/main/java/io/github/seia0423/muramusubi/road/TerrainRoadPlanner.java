package io.github.seia0423.muramusubi.road;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 地形に追従する道路中心線をA*で探索します。
 *
 * <p>Countered's Settlement Roads の RoadPathCalculator をNeoForge 26.2向けに
 * 再構成した実装です。元コードはCC0-1.0で公開されています。</p>
 */
public final class TerrainRoadPlanner {
    public static final int GRID_SIZE = 4;
    private static final int MAX_STEP_ELEVATION = 3;
    private static final int MAX_STABILITY_COST = 2;
    private static final int[][] NEIGHBOR_OFFSETS = {
            {GRID_SIZE, 0}, {-GRID_SIZE, 0}, {0, GRID_SIZE}, {0, -GRID_SIZE},
            {GRID_SIZE, GRID_SIZE}, {GRID_SIZE, -GRID_SIZE},
            {-GRID_SIZE, GRID_SIZE}, {-GRID_SIZE, -GRID_SIZE}
    };

    public Optional<RoadPlan> plan(GridPoint requestedStart, GridPoint requestedEnd,
            TerrainSampler terrainSampler, int maxSteps) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxStepsは1以上である必要があります");
        }

        Map<Long, TerrainSample> terrainCache = new HashMap<>();
        GridPoint start = snapToGrid(requestedStart);
        GridPoint end = snapToGrid(requestedEnd);
        int startY = sample(terrainSampler, terrainCache, start.x(), start.z()).surfaceY();
        int endY = sample(terrainSampler, terrainCache, end.x(), end.z()).surfaceY();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::fScore));
        Map<Long, Double> bestScores = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        Node startNode = new Node(start.x(), startY, start.z(), null, 0.0,
                heuristic(start.x(), start.z(), end.x(), end.z()));
        open.add(startNode);
        bestScores.put(hash(start.x(), start.z()), 0.0);

        int steps = 0;
        while (!open.isEmpty() && steps++ < maxSteps) {
            Node current = open.poll();
            long currentKey = hash(current.x(), current.z());
            if (!closed.add(currentKey)) {
                continue;
            }
            if (current.x() == end.x() && current.z() == end.z()) {
                return Optional.of(reconstruct(start, end, current));
            }

            for (int[] offset : NEIGHBOR_OFFSETS) {
                int nextX = current.x() + offset[0];
                int nextZ = current.z() + offset[1];
                long nextKey = hash(nextX, nextZ);
                if (closed.contains(nextKey)) {
                    continue;
                }

                TerrainSample nextTerrain = sample(terrainSampler, terrainCache, nextX, nextZ);
                int elevation = Math.abs(nextTerrain.surfaceY() - current.y());
                if (elevation > MAX_STEP_ELEVATION) {
                    continue;
                }

                int stability = terrainStability(nextX, nextZ, nextTerrain.surfaceY(),
                        terrainSampler, terrainCache);
                if (stability > MAX_STABILITY_COST) {
                    continue;
                }

                boolean diagonal = offset[0] != 0 && offset[1] != 0;
                double tentativeScore = current.gScore()
                        + (diagonal ? 1.5 : 1.0)
                        + elevation * 40.0
                        + (nextTerrain.aquatic() ? 400.0 : 0.0)
                        + (nextTerrain.surfaceY() == 62 ? 160.0 : 0.0)
                        + stability * 16.0;
                if (tentativeScore >= bestScores.getOrDefault(nextKey, Double.POSITIVE_INFINITY)) {
                    continue;
                }

                bestScores.put(nextKey, tentativeScore);
                double fScore = tentativeScore + heuristic(nextX, nextZ, end.x(), end.z());
                open.add(new Node(nextX, nextTerrain.surfaceY(), nextZ, current,
                        tentativeScore, fScore));
            }
        }

        return Optional.empty();
    }

    private static RoadPlan reconstruct(GridPoint start, GridPoint end, Node endNode) {
        List<GridPoint> gridPath = new ArrayList<>();
        for (Node node = endNode; node != null; node = node.parent()) {
            gridPath.add(new GridPoint(node.x(), node.z()));
        }
        Collections.reverse(gridPath);

        StraightRoadPlanner interpolator = new StraightRoadPlanner();
        List<GridPoint> centerLine = new ArrayList<>();
        centerLine.add(gridPath.getFirst());
        for (int index = 1; index < gridPath.size(); index++) {
            List<GridPoint> segment = interpolator.plan(gridPath.get(index - 1), gridPath.get(index)).centerLine();
            centerLine.addAll(segment.subList(1, segment.size()));
        }
        return new RoadPlan(start, end, centerLine);
    }

    private static int terrainStability(int x, int z, int y, TerrainSampler sampler,
            Map<Long, TerrainSample> cache) {
        int cost = 0;
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            cost += Math.abs(y - sample(sampler, cache, x + offset[0], z + offset[1]).surfaceY());
            if (cost > MAX_STABILITY_COST) {
                return cost;
            }
        }
        return cost;
    }

    private static TerrainSample sample(TerrainSampler sampler, Map<Long, TerrainSample> cache, int x, int z) {
        return cache.computeIfAbsent(hash(x, z), ignored -> sampler.sample(x, z));
    }

    private static double heuristic(int x, int z, int endX, int endZ) {
        int dx = Math.abs(x - endX);
        int dz = Math.abs(z - endZ);
        return (dx + dz - 0.6 * Math.min(dx, dz)) * 30.0;
    }

    private static GridPoint snapToGrid(GridPoint point) {
        return point.snappedTo(GRID_SIZE);
    }

    private static long hash(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private record Node(int x, int y, int z, Node parent, double gScore, double fScore) {
    }
}
