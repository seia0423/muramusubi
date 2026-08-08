package io.github.seia0423.muramusubi.road;

/**
 * 経路探索に必要な地形情報です。
 *
 * @param surfaceY 地表ブロックのY座標
 * @param aquatic 川・海など、水上道路として高コストにする地形か
 */
public record TerrainSample(int surfaceY, boolean aquatic) {
}
