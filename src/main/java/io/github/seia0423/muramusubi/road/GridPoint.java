package io.github.seia0423.muramusubi.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GridPoint(int x, int z) {
    public static final Codec<GridPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(GridPoint::x),
            Codec.INT.fieldOf("z").forGetter(GridPoint::z)
    ).apply(instance, GridPoint::new));

    public long squaredDistanceTo(GridPoint other) {
        long deltaX = (long) other.x - x;
        long deltaZ = (long) other.z - z;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    public GridPoint snappedTo(int gridSize) {
        if (gridSize < 1) {
            throw new IllegalArgumentException("gridSizeは1以上である必要があります");
        }
        return new GridPoint(
                Math.floorDiv(x, gridSize) * gridSize,
                Math.floorDiv(z, gridSize) * gridSize);
    }
}
