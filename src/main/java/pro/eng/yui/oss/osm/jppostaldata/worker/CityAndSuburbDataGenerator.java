package pro.eng.yui.oss.osm.jppostaldata.worker;

import pro.eng.yui.oss.osm.jppostaldata.Main;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CityAndSuburbDataGenerator extends AbstDataGenerator {

    public void generate() throws IOException {
        Path outputDir = Paths.get("pages", "master");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        Path outputPath = outputDir.resolve("cityAndSuburb.json");

        ArrayNode resultList = mapper.createArrayNode();
        JsonNode prefArray = loadPrefJson();

        if (prefArray != null && prefArray.isArray()) {
            for (JsonNode prefNode : prefArray) {
                String prefName = prefNode.path("name").asText();
                System.out.println(Main.FORMATTER.format(java.time.ZonedDateTime.now(Main.JST)) + " Processing " + prefName + " for cities and suburbs...");
                try {
                    fetchAndAddCities(resultList, prefName);
                } catch (Exception e) {
                    System.err.println("Failed to fetch cities for " + prefName + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), resultList);
        System.out.println("cityAndSuburb.jsonを生成しました: " + outputPath.toAbsolutePath());
    }

    private void fetchAndAddCities(ArrayNode resultList, String prefName) throws Exception {
        // 都道府県内の admin_level 7, 8 を取得
        String query = "[out:json][timeout:180];" +
                "area[\"name\"=\"" + prefName + "\"][\"admin_level\"=\"4\"]->.a;" +
                "(" +
                "  rel(area.a)[\"admin_level\"~\"^[78]$\"][\"boundary\"=\"administrative\"];" +
                ");" +
                "out body bb;" +
                "node(r:\"label\");" +
                "out body;";

        JsonNode root = executeOverpassQuery(query);
        if (root == null) return;

        JsonNode elements = root.get("elements");
        if (elements == null || !elements.isArray()) return;

        Map<Long, JsonNode> nodes = new HashMap<>();
        for (JsonNode el : elements) {
            if ("node".equals(el.path("type").asText())) {
                nodes.put(el.path("id").asLong(), el);
            }
        }

        for (JsonNode el : elements) {
            if ("relation".equals(el.path("type").asText())) {
                JsonNode tags = el.get("tags");
                if (tags == null) continue;

                String name = tags.path("name").asText();
                String adminLevel = tags.path("admin_level").asText();

                ObjectNode entry = mapper.createObjectNode();
                entry.put("name", name);
                entry.put("admin_level", adminLevel);

                JsonNode bounds = el.get("bounds");
                if (bounds != null) {
                    ObjectNode bbox = entry.putObject("bbox");
                    bbox.put("minLat", bounds.path("minlat").asDouble());
                    bbox.put("minLon", bounds.path("minlon").asDouble());
                    bbox.put("maxLat", bounds.path("maxlat").asDouble());
                    bbox.put("maxLon", bounds.path("maxlon").asDouble());
                }

                // label ノードを探す
                JsonNode members = el.get("members");
                if (members != null && members.isArray()) {
                    for (JsonNode m : members) {
                        if ("label".equals(m.path("role").asText()) && "node".equals(m.path("type").asText())) {
                            long nodeId = m.path("ref").asLong();
                            JsonNode nodeEl = nodes.get(nodeId);
                            if (nodeEl != null) {
                                ObjectNode label = entry.putObject("label");
                                label.put("lat", nodeEl.path("lat").asDouble());
                                label.put("lon", nodeEl.path("lon").asDouble());
                            }
                            break;
                        }
                    }
                }
                resultList.add(entry);
            }
        }
    }
}
