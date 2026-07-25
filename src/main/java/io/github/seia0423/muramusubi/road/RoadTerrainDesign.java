package io.github.seia0423.muramusubi.road;

import java.util.ArrayList;
import java.util.List;

/** 地形サンプル列から橋区間・段差・切土盛土後の高さを計画します。 */
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
        return slopeTransitionsFromHeights(samples.stream().map(TerrainSample::surfaceY).toList());
    }

    public static List<SlopeTransition> slopeTransitionsFromHeights(List<Integer> heights) {
        List<SlopeTransition> transitions = new ArrayList<>();
        for (int index = 0; index + 1 < heights.size(); index++) {
            int difference = heights.get(index + 1) - heights.get(index);
            if (Math.abs(difference) == 1) {
                transitions.add(difference > 0
                        ? new SlopeTransition(index, index + 1)
                        : new SlopeTransition(index + 1, index));
            }
        }
        return List.copyOf(transitions);
    }

    public static SlopeDirection slopeDirection(GridPoint lower, GridPoint higher) {
        int stepX = Integer.signum(higher.x() - lower.x());
        int stepZ = Integer.signum(higher.z() - lower.z());
        if (stepX == 0 && stepZ == 0) {
            throw new IllegalArgumentException("Slope endpoints must be different");
        }
        if (stepX != 0 && stepZ != 0) {
            return SlopeDirection.DIAGONAL;
        }
        if (stepX > 0) {
            return SlopeDirection.EAST;
        }
        if (stepX < 0) {
            return SlopeDirection.WEST;
        }
        return stepZ > 0 ? SlopeDirection.SOUTH : SlopeDirection.NORTH;
    }

    public static List<Integer> gradeSurfaceHeights(
            List<TerrainSample> samples, int maximumAdjustment) {
        if (maximumAdjustment < 0) {
            throw new IllegalArgumentException("maximumAdjustmentは0以上である必要があります");
        }
        List<Integer> original = samples.stream().map(TerrainSample::surfaceY).toList();
        List<Integer> graded = new ArrayList<>(original);
        boolean changed;
        int remainingPasses = Math.max(1, samples.size() * (maximumAdjustment + 1));
        do {
            changed = false;
            for (int index = 0; index + 1 < graded.size(); index++) {
                int first = graded.get(index);
                int second = graded.get(index + 1);
                while (Math.abs(second - first) > 1) {
                    int lowerIndex = first < second ? index : index + 1;
                    int higherIndex = first < second ? index + 1 : index;
                    int lowerAdjustment = graded.get(lowerIndex) - original.get(lowerIndex);
                    int higherAdjustment = original.get(higherIndex) - graded.get(higherIndex);
                    boolean canRaiseLower = lowerAdjustment < maximumAdjustment;
                    boolean canLowerHigher = higherAdjustment < maximumAdjustment;
                    if (!canRaiseLower && !canLowerHigher) {
                        break;
                    }
                    if (canRaiseLower && (!canLowerHigher || lowerAdjustment <= higherAdjustment)) {
                        graded.set(lowerIndex, graded.get(lowerIndex) + 1);
                    } else {
                        graded.set(higherIndex, graded.get(higherIndex) - 1);
                    }
                    changed = true;
                    first = graded.get(index);
                    second = graded.get(index + 1);
                }
            }
        } while (changed && --remainingPasses > 0);
        return List.copyOf(graded);
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

    public enum SlopeDirection {
        NORTH(0),
        EAST(1),
        SOUTH(2),
        WEST(3),
        DIAGONAL(-1);

        private final int code;

        SlopeDirection(int code) {
            this.code = code;
        }

        public int code() {
            if (this == DIAGONAL) {
                throw new IllegalStateException("Diagonal slopes do not have a stair facing");
            }
            return code;
        }
    }
}
