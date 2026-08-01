package pro.eng.yui.oss.osm.jppostaldata.worker;

import pro.eng.yui.oss.osm.jppostaldata.Main;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class BoundaryDataGenerator extends AbstDataGenerator {

    public void generate() throws IOException {
        Path outputDir = Paths.get("pages", "master");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        Path outputPath = outputDir.resolve("boundary.json");

        // 1. 既存データのロード
        JsonNode existing = loadExistingData("master/boundary.json");
        ObjectNode boundaryData = (existing != null && existing.isObject()) ? (ObjectNode) existing : mapper.createObjectNode();

        // 2. pref.json のロード
        JsonNode prefArray = loadPrefJson();

        // 3. 各都道府県の処理
        if (prefArray != null && prefArray.isArray()) {
            for (JsonNode prefNode : prefArray) {
                String prefName = prefNode.path("name").asText();
                String prefCode = prefNode.path("code").asText();
                // コードが数値の場合もあるので整形 (01, 02...)
                try {
                    prefCode = String.format("%02d", Integer.parseInt(prefCode));
                } catch (NumberFormatException ignored) {}

                String subFile = prefNode.has("sub") ? prefNode.get("sub").asText() : null;

                ObjectNode prefBoundary = boundaryData.has(prefCode) ?
                        (ObjectNode) boundaryData.get(prefCode) : boundaryData.putObject(prefCode);
                ObjectNode subMap = prefBoundary.has("sub") ?
                        (ObjectNode) prefBoundary.get("sub") : prefBoundary.putObject("sub");

                // "00" (都道府県本体) の取得
                fetchAndSetBoundary(subMap, "00", "4", prefName);

                // sub がある場合の取得
                if (subFile != null) {
                    JsonNode subArray = loadSubJson(subFile);
                    if (subArray != null && subArray.isArray()) {
                        for (JsonNode subNode : subArray) {
                            String subCode = subNode.path("code").asText();
                            String subAdminLevel = subNode.path("admin_level").asText();
                            String subName = subNode.path("name").asText();
                            fetchAndSetBoundary(subMap, subCode, subAdminLevel, subName);
                        }
                    }
                }
            }
        }

        // 4. 書き出し
        simplifiedWriter.writeValue(outputPath.toFile(), boundaryData);
        System.out.println("boundary.jsonを生成しました: " + outputPath.toAbsolutePath());
    }


    private JsonNode loadSubJson(String subFile) throws IOException {
        try (InputStream is = Main.class.getResourceAsStream("/content/master/sub/" + subFile)) {
            if (is == null) return null;
            return mapper.readTree(is);
        }
    }

    private void fetchAndSetBoundary(ObjectNode subMap, String subCode, String adminLevel, String name) {
        try {
            Map<String, Double> bbox = fetchBoundaryFromOverpass(adminLevel, name);
            if (bbox != null) {
                // validation: ignore if all values are 0.0
                if (bbox.get("minlon") == 0.0 && bbox.get("maxlon") == 0.0 &&
                        bbox.get("minlat") == 0.0 && bbox.get("maxlat") == 0.0) {
                    System.err.println("Warning: Boundary data for " + name + " is all 0.0, skipping.");
                    return;
                }

                ObjectNode bboxNode = subMap.putObject(subCode);
                bboxNode.put("minLon", bbox.get("minlon"));
                bboxNode.put("maxLon", bbox.get("maxlon"));
                bboxNode.put("minLat", bbox.get("minlat"));
                bboxNode.put("maxLat", bbox.get("maxlat"));
            } else {
                // If bbox is null, we keep existing data if present in subMap
                if (subMap.has(subCode)) {
                    System.out.println("Notice: Could not fetch boundary for " + name + ", keeping existing data.");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch boundary: " + name + " (" + adminLevel + ") " + e.getMessage());
            if (subMap.has(subCode)) {
                System.out.println("Notice: Keeping existing boundary data for " + name + " due to error.");
            }
        }
    }

    private Map<String, Double> fetchBoundaryFromOverpass(String adminLevel, String name) throws Exception {
        String query = "[out:json][timeout:120];rel[\"admin_level\"=\"" + adminLevel + "\"][\"name\"=\"" + name + "\"];out bb;";

        JsonNode root = executeOverpassQuery(query);
        if (root != null) {
            JsonNode elements = root.get("elements");
            if (elements != null && elements.isArray() && elements.size() > 0) {
                JsonNode bounds = elements.get(0).get("bounds");
                if (bounds != null) {
                    Map<String, Double> res = new HashMap<>();
                    res.put("minlat", bounds.path("minlat").asDouble());
                    res.put("minlon", bounds.path("minlon").asDouble());
                    res.put("maxlat", bounds.path("maxlat").asDouble());
                    res.put("maxlon", bounds.path("maxlon").asDouble());
                    return res;
                }
            }
        }
        return null;
    }
}
