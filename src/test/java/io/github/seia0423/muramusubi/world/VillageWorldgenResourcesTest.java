package io.github.seia0423.muramusubi.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class VillageWorldgenResourcesTest {
    private static final List<String> BIOMES = List.of(
            "plains", "desert", "savanna", "snowy", "taiga");

    @Test
    void addsDenseLargeVillagesWithoutReplacingVanillaVillages() throws IOException {
        JsonObject structureSet = resource(
                "/data/muramusubi/worldgen/structure_set/large_villages.json");
        JsonObject placement = structureSet.getAsJsonObject("placement");

        assertEquals(20, placement.get("spacing").getAsInt());
        assertEquals(8, placement.get("separation").getAsInt());
        assertEquals("minecraft:villages",
                placement.getAsJsonObject("exclusion_zone").get("other_set").getAsString());
        assertEquals(5, structureSet.getAsJsonArray("structures").size());

        JsonObject villageTag = resource(
                "/data/minecraft/tags/worldgen/structure/village.json");
        assertFalse(villageTag.get("replace").getAsBoolean());
        assertEquals(5, villageTag.getAsJsonArray("values").size());
    }

    @Test
    void reusesOnlyVanillaVillageBuildingPools() throws IOException {
        for (String biome : BIOMES) {
            JsonObject structure = resource(
                    "/data/muramusubi/worldgen/structure/large_village_" + biome + ".json");

            assertEquals(10, structure.get("size").getAsInt());
            assertEquals(112, structure.get("max_distance_from_center").getAsInt());
            assertEquals("#minecraft:has_structure/village_" + biome,
                    structure.get("biomes").getAsString());
            assertEquals("minecraft:village/" + biome + "/town_centers",
                    structure.get("start_pool").getAsString());
        }
    }

    private static JsonObject resource(String path) throws IOException {
        var stream = VillageWorldgenResourcesTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing test resource: " + path);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
