package io.github.seia0423.muramusubi.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoadTerrainDesignTest {
    @Test
    void findsOnlyAquaticRunsMeetingMinimumLength() {
        List<TerrainSample> samples = List.of(
                land(64), water(62), water(62), land(63),
                water(62), water(62), water(62), land(64));

        assertEquals(
                List.of(new RoadTerrainDesign.BridgeSpan(4, 6)),
                RoadTerrainDesign.bridgeSpans(samples, 3));
    }

    @Test
    void includesAquaticRunAtEndOfPath() {
        List<TerrainSample> samples = List.of(land(64), water(62), water(62));

        assertEquals(
                List.of(new RoadTerrainDesign.BridgeSpan(1, 2)),
                RoadTerrainDesign.bridgeSpans(samples, 2));
    }

    @Test
    void findsAscendingAndDescendingSingleBlockTransitions() {
        List<TerrainSample> samples = List.of(land(64), land(65), land(65), land(64), land(61));

        assertEquals(
                List.of(
                        new RoadTerrainDesign.SlopeTransition(0, 1),
                        new RoadTerrainDesign.SlopeTransition(3, 2)),
                RoadTerrainDesign.slopeTransitions(samples));
    }

    @Test
    void rejectsInvalidMinimumBridgeLength() {
        assertThrows(IllegalArgumentException.class,
                () -> RoadTerrainDesign.bridgeSpans(List.of(), 0));
    }

    @Test
    void gradesThreeBlockRiseIntoWalkableProfile() {
        List<TerrainSample> samples = List.of(land(64), land(64), land(67), land(67));

        assertEquals(
                List.of(64, 65, 66, 67),
                RoadTerrainDesign.gradeSurfaceHeights(samples, 3));
    }

    @Test
    void limitsCutAndFillOnExtremeCliff() {
        List<TerrainSample> samples = List.of(land(64), land(72));

        assertEquals(
                List.of(67, 69),
                RoadTerrainDesign.gradeSurfaceHeights(samples, 3));
    }

    @Test
    void rejectsNegativeMaximumAdjustment() {
        assertThrows(IllegalArgumentException.class,
                () -> RoadTerrainDesign.gradeSurfaceHeights(List.of(), -1));
    }

    @Test
    void pointsCardinalStairsFromLowerGroundTowardHigherGround() {
        GridPoint lower = new GridPoint(10, 20);

        assertEquals(RoadTerrainDesign.SlopeDirection.NORTH,
                RoadTerrainDesign.slopeDirection(lower, new GridPoint(10, 19)));
        assertEquals(RoadTerrainDesign.SlopeDirection.EAST,
                RoadTerrainDesign.slopeDirection(lower, new GridPoint(11, 20)));
        assertEquals(RoadTerrainDesign.SlopeDirection.SOUTH,
                RoadTerrainDesign.slopeDirection(lower, new GridPoint(10, 21)));
        assertEquals(RoadTerrainDesign.SlopeDirection.WEST,
                RoadTerrainDesign.slopeDirection(lower, new GridPoint(9, 20)));
    }

    @Test
    void marksDiagonalSlopesAsDirectionless() {
        assertEquals(RoadTerrainDesign.SlopeDirection.DIAGONAL,
                RoadTerrainDesign.slopeDirection(new GridPoint(10, 20), new GridPoint(11, 19)));
    }

    @Test
    void rejectsSlopeDirectionForTheSamePoint() {
        GridPoint point = new GridPoint(10, 20);

        assertThrows(IllegalArgumentException.class,
                () -> RoadTerrainDesign.slopeDirection(point, point));
    }

    private static TerrainSample land(int y) {
        return new TerrainSample(y, false);
    }

    private static TerrainSample water(int y) {
        return new TerrainSample(y, true);
    }
}
