package io.github.seia0423.muramusubi.road;

import java.util.List;

public record RoadPlan(GridPoint start, GridPoint end, List<GridPoint> centerLine) {
    public RoadPlan {
        centerLine = List.copyOf(centerLine);
        if (centerLine.isEmpty()) {
            throw new IllegalArgumentException("道路の中心線は空にできません");
        }
        if (!centerLine.getFirst().equals(start) || !centerLine.getLast().equals(end)) {
            throw new IllegalArgumentException("道路の中心線は始点と終点を含む必要があります");
        }
    }

    public int lengthInBlocks() {
        return centerLine.size();
    }
}
