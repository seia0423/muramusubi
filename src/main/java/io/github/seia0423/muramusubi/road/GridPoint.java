package io.github.seia0423.muramusubi.road;

public record GridPoint(int x, int z) {
    public long squaredDistanceTo(GridPoint other) {
        long deltaX = (long) other.x - x;
        long deltaZ = (long) other.z - z;
        return deltaX * deltaX + deltaZ * deltaZ;
    }
}
