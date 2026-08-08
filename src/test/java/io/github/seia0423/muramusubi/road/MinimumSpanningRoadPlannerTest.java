package io.github.seia0423.muramusubi.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MinimumSpanningRoadPlannerTest {
    private final MinimumSpanningRoadPlanner planner = new MinimumSpanningRoadPlanner();

    @Test
    void connectsAllVillagesWithoutCycles() {
        GridPoint first = new GridPoint(0, 0);
        GridPoint second = new GridPoint(100, 0);
        GridPoint third = new GridPoint(200, 0);
        GridPoint fourth = new GridPoint(100, 100);

        RoadNetworkPlan plan = planner.plan(List.of(first, second, third, fourth), 150);

        assertTrue(plan.isFullyConnected());
        assertEquals(3, plan.connections().size());
    }

    @Test
    void reportsDisconnectedVillageGroups() {
        RoadNetworkPlan plan = planner.plan(List.of(
                new GridPoint(0, 0),
                new GridPoint(50, 0),
                new GridPoint(1_000, 0)), 100);

        assertFalse(plan.isFullyConnected());
        assertEquals(2, plan.componentCount());
        assertEquals(1, plan.connections().size());
    }

    @Test
    void ignoresDuplicateVillageLocations() {
        GridPoint village = new GridPoint(12, 34);

        RoadNetworkPlan plan = planner.plan(List.of(village, village), 100);

        assertTrue(plan.isFullyConnected());
        assertTrue(plan.connections().isEmpty());
    }
}
