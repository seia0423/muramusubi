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

    private static TerrainSample land(int y) {
        return new TerrainSample(y, false);
    }

    private static TerrainSample water(int y) {
        return new TerrainSample(y, true);
    }
}
