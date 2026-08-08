package io.github.seia0423.muramusubi.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StraightRoadPlannerTest {
    private final StraightRoadPlanner planner = new StraightRoadPlanner();

    @Test
    void includesBothEndsForHorizontalRoad() {
        RoadPlan plan = planner.plan(new GridPoint(2, 4), new GridPoint(5, 4));

        assertEquals(List.of(
                new GridPoint(2, 4),
                new GridPoint(3, 4),
                new GridPoint(4, 4),
                new GridPoint(5, 4)), plan.centerLine());
    }

    @Test
    void plansDiagonalRoadInEitherDirection() {
        RoadPlan plan = planner.plan(new GridPoint(3, 3), new GridPoint(0, 0));

        assertEquals(List.of(
                new GridPoint(3, 3),
                new GridPoint(2, 2),
                new GridPoint(1, 1),
                new GridPoint(0, 0)), plan.centerLine());
    }

    @Test
    void onePointRoadContainsOneBlock() {
        RoadPlan plan = planner.plan(new GridPoint(-7, 12), new GridPoint(-7, 12));

        assertEquals(1, plan.lengthInBlocks());
    }

    @Test
    void roadPlanRejectsCenterLineThatDoesNotMatchEnds() {
        assertThrows(IllegalArgumentException.class, () -> new RoadPlan(
                new GridPoint(0, 0),
                new GridPoint(2, 0),
                List.of(new GridPoint(1, 0))));
    }

    @Test
    void gridSnappingUsesFloorDivisionForNegativeCoordinates() {
        assertEquals(new GridPoint(-4, -8), new GridPoint(-1, -5).snappedTo(4));
    }
}
