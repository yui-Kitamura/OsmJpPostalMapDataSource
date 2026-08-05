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
        generate(null);
    }

    public void generate(String targetPrefecture) throws IOException {
        Path outputDir = Paths.get("pages", "master");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        Path outputPath = outputDir.resolve("cityAndSuburb.json");

        JsonNode existing = loadExistingData("master/cityAndSuburb.json");
        ArrayNode resultList;
        if (existing != null && existing.isArray()) {
            resultList = (ArrayNode) existing;
        } else {
            resultList = mapper.createArrayNode();
        }

        JsonNode prefArray = loadPrefJson();

        if (prefArray != null && prefArray.isArray()) {
            for (JsonNode prefNode : prefArray) {
                String prefName = prefNode.path("name").asText();
                String prefCodeRaw = prefNode.path("code").asText();
                String prefCode = prefCodeRaw;
                try {
                    prefCode = String.format("%02d", Integer.parseInt(prefCodeRaw));
                } catch (NumberFormatException ignored) {}

                boolean shouldUpdate = (targetPrefecture == null) ||
                        targetPrefecture.equals(prefName) ||
                        targetPrefecture.equals(prefCodeRaw) ||
                        targetPrefecture.equals(prefCode);

                if (!shouldUpdate) {
                    continue;
                }

                System.out.println(Main.FORMATTER.format(java.time.ZonedDateTime.now(Main.JST)) + " Processing " + prefName + " for cities and suburbs...");
                try {
                    // Fetch into a temporary array to ensure we have data before removing old ones
                    ArrayNode temp = mapper.createArrayNode();
                    fetchAndAddCities(temp, prefName, prefCode);

                    if (temp.size() > 0) {
                        // target found or full run, remove existing entries for this prefCode
                        for (int i = resultList.size() - 1; i >= 0; i--) {
                            if (prefCode.equals(resultList.get(i).path("is_in").asText())) {
                                resultList.remove(i);
                            }
                        }
                        resultList.addAll(temp);
                    } else {
                        System.out.println("No data returned for " + prefName + ", keeping existing data.");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to fetch cities for " + prefName + ", keeping existing data: " + e.getMessage());
                }
            }
        }

        simplifiedWriter.writeValue(outputPath.toFile(), resultList);
        System.out.println("cityAndSuburb.jsonを生成しました: " + outputPath.toAbsolutePath());
    }

    private void fetchAndAddCities(ArrayNode resultList, String prefName, String prefCode) throws Exception {
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
                String kana = tags.path("name:ja-Hira").asText();

                ObjectNode entry = mapper.createObjectNode();
                entry.put("name", name);
                if (kana != null && !kana.isEmpty()) {
                    entry.put("kana", kana);
                }

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
                entry.put("is_in", prefCode);
                resultList.add(entry);
            }
        }
    }
}
