package io.github.seia0423.muramusubi.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerrainRoadPlannerTest {
    private final TerrainRoadPlanner planner = new TerrainRoadPlanner();

    @Test
    void findsStraightPathOnFlatGround() {
        Optional<RoadPlan> result = planner.plan(
                new GridPoint(0, 0),
                new GridPoint(16, 0),
                (x, z) -> new TerrainSample(64, false),
                1_000);

        assertTrue(result.isPresent());
        assertEquals(new GridPoint(0, 0), result.get().centerLine().getFirst());
        assertEquals(new GridPoint(16, 0), result.get().centerLine().getLast());
        assertEquals(17, result.get().lengthInBlocks());
    }

    @Test
    void avoidsAnImpassableCliff() {
        Optional<RoadPlan> result = planner.plan(
                new GridPoint(0, 0),
                new GridPoint(16, 0),
                (x, z) -> new TerrainSample(x == 8 && Math.abs(z) < 8 ? 80 : 64, false),
                10_000);

        assertTrue(result.isPresent());
        assertTrue(result.get().centerLine().stream()
                .noneMatch(point -> point.x() == 8 && Math.abs(point.z()) < 8));
    }

    @Test
    void returnsEmptyWhenStepBudgetIsExhausted() {
        Optional<RoadPlan> result = planner.plan(
                new GridPoint(0, 0),
                new GridPoint(4_000, 0),
                (x, z) -> new TerrainSample(64, false),
                2);

        assertTrue(result.isEmpty());
    }

    @Test
    void snapsNegativeCoordinatesUsingFloorDivision() {
        Optional<RoadPlan> result = planner.plan(
                new GridPoint(-1, -1),
                new GridPoint(-9, -1),
                (x, z) -> new TerrainSample(64, false),
                1_000);

        assertTrue(result.isPresent());
        assertEquals(new GridPoint(-4, -4), result.get().start());
        assertEquals(new GridPoint(-12, -4), result.get().end());
    }
}
