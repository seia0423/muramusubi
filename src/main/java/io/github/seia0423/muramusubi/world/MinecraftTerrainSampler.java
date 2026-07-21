package io.github.seia0423.muramusubi.world;

import io.github.seia0423.muramusubi.road.TerrainSample;
import io.github.seia0423.muramusubi.road.TerrainSampler;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;

/** チャンクを明示的に生成せず、ワールド生成器から地表情報を取得します。 */
public final class MinecraftTerrainSampler implements TerrainSampler {
    private final ServerLevel level;
    private final Map<Long, TerrainSample> cache = new HashMap<>();

    public MinecraftTerrainSampler(ServerLevel level) {
        this.level = level;
    }

    @Override
    public TerrainSample sample(int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        return cache.computeIfAbsent(key, ignored -> sampleUncached(x, z));
    }

    private TerrainSample sampleUncached(int x, int z) {
        var chunkSource = level.getChunkSource();
        int firstFreeY = chunkSource.getGenerator().getBaseHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, level, chunkSource.randomState());
        int surfaceY = firstFreeY - 1;
        var biome = level.getUncachedNoiseBiome(x >> 2, surfaceY >> 2, z >> 2);
        boolean aquatic = biome.is(BiomeTags.IS_RIVER)
                || biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN);
        return new TerrainSample(surfaceY, aquatic);
    }
}
