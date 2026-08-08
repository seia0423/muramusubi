package io.github.seia0423.muramusubi.road;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 距離の短い接続を優先するKruskal法で、村の最小全域森を作ります。 */
public final class MinimumSpanningRoadPlanner {
    public RoadNetworkPlan plan(List<GridPoint> inputVillages, int maximumDistance) {
        List<GridPoint> villages = List.copyOf(new LinkedHashSet<>(inputVillages));
        if (villages.isEmpty()) {
            return new RoadNetworkPlan(List.of(), 0);
        }

        long maximumSquaredDistance = (long) maximumDistance * maximumDistance;
        List<Candidate> candidates = new ArrayList<>();
        for (int first = 0; first < villages.size(); first++) {
            for (int second = first + 1; second < villages.size(); second++) {
                long squaredDistance = villages.get(first).squaredDistanceTo(villages.get(second));
                if (squaredDistance <= maximumSquaredDistance) {
                    candidates.add(new Candidate(first, second, squaredDistance));
                }
            }
        }
        candidates.sort(Comparator.comparingLong(Candidate::squaredDistance));

        DisjointSet components = new DisjointSet(villages.size());
        List<RoadConnection> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (components.union(candidate.firstIndex(), candidate.secondIndex())) {
                selected.add(RoadConnection.between(
                        villages.get(candidate.firstIndex()),
                        villages.get(candidate.secondIndex())));
            }
        }
        return new RoadNetworkPlan(selected, components.componentCount());
    }

    private record Candidate(int firstIndex, int secondIndex, long squaredDistance) {
    }

    private static final class DisjointSet {
        private final Map<Integer, Integer> parent = new HashMap<>();
        private final Map<Integer, Integer> rank = new HashMap<>();
        private int componentCount;

        private DisjointSet(int size) {
            componentCount = size;
            for (int index = 0; index < size; index++) {
                parent.put(index, index);
                rank.put(index, 0);
            }
        }

        private int find(int node) {
            int currentParent = parent.get(node);
            if (currentParent != node) {
                parent.put(node, find(currentParent));
            }
            return parent.get(node);
        }

        private boolean union(int first, int second) {
            int firstRoot = find(first);
            int secondRoot = find(second);
            if (firstRoot == secondRoot) {
                return false;
            }

            int firstRank = rank.get(firstRoot);
            int secondRank = rank.get(secondRoot);
            if (firstRank < secondRank) {
                parent.put(firstRoot, secondRoot);
            } else if (firstRank > secondRank) {
                parent.put(secondRoot, firstRoot);
            } else {
                parent.put(secondRoot, firstRoot);
                rank.put(firstRoot, firstRank + 1);
            }
            componentCount--;
            return true;
        }

        private int componentCount() {
            return componentCount;
        }
    }
}
