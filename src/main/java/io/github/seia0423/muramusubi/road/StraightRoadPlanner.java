package io.github.seia0423.muramusubi.road;

import java.util.ArrayList;
import java.util.List;

/**
 * MinecraftのAPIに依存しない、道路中心線の最小実装です。
 * 地形コストを扱う経路探索へ置き換えるまでの基準実装として使います。
 */
public final class StraightRoadPlanner {
    public RoadPlan plan(GridPoint start, GridPoint end) {
        List<GridPoint> points = new ArrayList<>();
        int x = start.x();
        int z = start.z();
        int deltaX = Math.abs(end.x() - x);
        int stepX = x < end.x() ? 1 : -1;
        int deltaZ = -Math.abs(end.z() - z);
        int stepZ = z < end.z() ? 1 : -1;
        int error = deltaX + deltaZ;

        while (true) {
            points.add(new GridPoint(x, z));
            if (x == end.x() && z == end.z()) {
                return new RoadPlan(start, end, points);
            }

            int doubledError = error * 2;
            if (doubledError >= deltaZ) {
                error += deltaZ;
                x += stepX;
            }
            if (doubledError <= deltaX) {
                error += deltaX;
                z += stepZ;
            }
        }
    }
}
