package io.github.seia0423.muramusubi.road;

@FunctionalInterface
public interface TerrainSampler {
    TerrainSample sample(int x, int z);
}
