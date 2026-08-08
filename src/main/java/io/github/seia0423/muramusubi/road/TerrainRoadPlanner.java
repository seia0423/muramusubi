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
        Search search = begin(requestedStart, requestedEnd, terrainSampler, maxSteps);
        search.advance(maxSteps);
        return search.result();
    }

    public Search begin(GridPoint requestedStart, GridPoint requestedEnd,
            TerrainSampler terrainSampler, int maxSteps) {
        return new Search(requestedStart, requestedEnd, terrainSampler, maxSteps);
    }

    public enum SearchStatus {
        RUNNING,
        FOUND,
        FAILED
    }

    /** 複数ティックに分けて進められるA*探索状態です。 */
    public static final class Search {
        private final GridPoint start;
        private final GridPoint end;
        private final TerrainSampler terrainSampler;
        private final int maxSteps;
        private final Map<Long, TerrainSample> terrainCache = new HashMap<>();
        private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::fScore));
        private final Map<Long, Double> bestScores = new HashMap<>();
        private final Set<Long> closed = new HashSet<>();
        private SearchStatus status = SearchStatus.RUNNING;
        private RoadPlan result;
        private int visitedSteps;

        private Search(GridPoint requestedStart, GridPoint requestedEnd,
                TerrainSampler terrainSampler, int maxSteps) {
            if (maxSteps < 1) {
                throw new IllegalArgumentException("maxStepsは1以上である必要があります");
            }
            this.start = requestedStart.snappedTo(GRID_SIZE);
            this.end = requestedEnd.snappedTo(GRID_SIZE);
            this.terrainSampler = terrainSampler;
            this.maxSteps = maxSteps;

            int startY = sample(start.x(), start.z()).surfaceY();
            Node startNode = new Node(start.x(), startY, start.z(), null, 0.0,
                    heuristic(start.x(), start.z(), end.x(), end.z()));
            open.add(startNode);
            bestScores.put(hash(start.x(), start.z()), 0.0);
        }

        public SearchStatus advance(int stepBudget) {
            if (stepBudget < 1) {
                throw new IllegalArgumentException("stepBudgetは1以上である必要があります");
            }
            if (status != SearchStatus.RUNNING) {
                return status;
            }

            int advanced = 0;
            while (!open.isEmpty() && visitedSteps < maxSteps && advanced < stepBudget) {
                Node current = open.poll();
                visitedSteps++;
                advanced++;
                long currentKey = hash(current.x(), current.z());
                if (!closed.add(currentKey)) {
                    continue;
                }
                if (current.x() == end.x() && current.z() == end.z()) {
                    result = reconstruct(start, end, current);
                    status = SearchStatus.FOUND;
                    return status;
                }

                visitNeighbors(current);
            }

            if (open.isEmpty() || visitedSteps >= maxSteps) {
                status = SearchStatus.FAILED;
            }
            return status;
        }

        public SearchStatus status() {
            return status;
        }

        public Optional<RoadPlan> result() {
            return Optional.ofNullable(result);
        }

        public int visitedSteps() {
            return visitedSteps;
        }

        private void visitNeighbors(Node current) {
            for (int[] offset : NEIGHBOR_OFFSETS) {
                int nextX = current.x() + offset[0];
                int nextZ = current.z() + offset[1];
                long nextKey = hash(nextX, nextZ);
                if (closed.contains(nextKey)) {
                    continue;
                }

                TerrainSample nextTerrain = sample(nextX, nextZ);
                int elevation = Math.abs(nextTerrain.surfaceY() - current.y());
                if (elevation > MAX_STEP_ELEVATION) {
                    continue;
                }

                int stability = terrainStability(nextX, nextZ, nextTerrain.surfaceY());
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

        private int terrainStability(int x, int z, int y) {
            int cost = 0;
            int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : offsets) {
                cost += Math.abs(y - sample(x + offset[0], z + offset[1]).surfaceY());
                if (cost > MAX_STABILITY_COST) {
                    return cost;
                }
            }
            return cost;
        }

        private TerrainSample sample(int x, int z) {
            return terrainCache.computeIfAbsent(hash(x, z),
                    ignored -> terrainSampler.sample(x, z));
        }
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

    private static double heuristic(int x, int z, int endX, int endZ) {
        int dx = Math.abs(x - endX);
        int dz = Math.abs(z - endZ);
        return (dx + dz - 0.6 * Math.min(dx, dz)) * 30.0;
    }

    private static long hash(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private record Node(int x, int y, int z, Node parent, double gScore, double fScore) {
    }
}
