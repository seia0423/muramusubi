package io.github.seia0423.muramusubi.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 始点と終点の順序に依存しない道路接続です。 */
public record RoadConnection(GridPoint first, GridPoint second) {
    public static final Codec<RoadConnection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GridPoint.CODEC.fieldOf("first").forGetter(RoadConnection::first),
            GridPoint.CODEC.fieldOf("second").forGetter(RoadConnection::second)
    ).apply(instance, RoadConnection::between));

    public RoadConnection {
        if (compare(first, second) > 0) {
            throw new IllegalArgumentException("道路接続は正規化された順序である必要があります");
        }
    }

    public static RoadConnection between(GridPoint first, GridPoint second) {
        return compare(first, second) <= 0
                ? new RoadConnection(first, second)
                : new RoadConnection(second, first);
    }

    private static int compare(GridPoint first, GridPoint second) {
        int xComparison = Integer.compare(first.x(), second.x());
        return xComparison != 0 ? xComparison : Integer.compare(first.z(), second.z());
    }
}
