package io.github.seia0423.muramusubi.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.seia0423.muramusubi.road.GridPoint;
import java.util.Locale;

/** 再起動後にも再開できる、道路または装飾の1ブロック分の設置命令です。 */
public record RoadBuildOperation(GridPoint point, Kind kind, int verticalOffset, int palette) {
    private static final Codec<Kind> KIND_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(Kind.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Unknown road build operation kind: " + value);
                }
            },
            kind -> kind.name().toLowerCase(Locale.ROOT));

    public static final Codec<RoadBuildOperation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GridPoint.CODEC.fieldOf("point").forGetter(RoadBuildOperation::point),
            KIND_CODEC.fieldOf("kind").forGetter(RoadBuildOperation::kind),
            Codec.INT.optionalFieldOf("vertical_offset", 0).forGetter(RoadBuildOperation::verticalOffset),
            Codec.INT.optionalFieldOf("palette", 0).forGetter(RoadBuildOperation::palette)
    ).apply(instance, RoadBuildOperation::new));

    public enum Kind {
        ROAD_ARTIFICIAL,
        ROAD_NATURAL,
        SLOPE_STAIR,
        SLOPE_SLAB,
        SLOPE_ARTIFICIAL_SLAB,
        TERRAIN_FILL,
        TERRAIN_CLEAR,
        BRIDGE_DECK,
        BRIDGE_RAIL,
        BRIDGE_PILLAR,
        FENCE,
        TORCH,
        LANTERN,
        HANGING_LANTERN
    }
}
