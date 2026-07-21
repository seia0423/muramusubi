package io.github.seia0423.muramusubi.road;

import java.util.ArrayList;
import java.util.List;

/** 地形サンプル列から橋区間と1ブロック段差を抽出します。 */
public final class RoadTerrainDesign {
    private RoadTerrainDesign() {
    }

    public static List<BridgeSpan> bridgeSpans(List<TerrainSample> samples, int minimumLength) {
        if (minimumLength < 1) {
            throw new IllegalArgumentException("minimumLengthは1以上である必要があります");
        }
        List<BridgeSpan> spans = new ArrayList<>();
        int start = -1;
        for (int index = 0; index <= samples.size(); index++) {
            boolean aquatic = index < samples.size() && samples.get(index).aquatic();
            if (aquatic && start < 0) {
                start = index;
            } else if (!aquatic && start >= 0) {
                if (index - start >= minimumLength) {
                    spans.add(new BridgeSpan(start, index - 1));
                }
                start = -1;
            }
        }
        return List.copyOf(spans);
    }

    public static List<SlopeTransition> slopeTransitions(List<TerrainSample> samples) {
        List<SlopeTransition> transitions = new ArrayList<>();
        for (int index = 0; index + 1 < samples.size(); index++) {
            int difference = samples.get(index + 1).surfaceY() - samples.get(index).surfaceY();
            if (Math.abs(difference) == 1) {
                transitions.add(difference > 0
                        ? new SlopeTransition(index, index + 1)
                        : new SlopeTransition(index + 1, index));
            }
        }
        return List.copyOf(transitions);
    }

    public record BridgeSpan(int firstIndex, int lastIndex) {
        public BridgeSpan {
            if (firstIndex < 0 || lastIndex < firstIndex) {
                throw new IllegalArgumentException("橋区間のインデックスが不正です");
            }
        }

        public int length() {
            return lastIndex - firstIndex + 1;
        }
    }

    public record SlopeTransition(int lowerIndex, int higherIndex) {
    }
}
