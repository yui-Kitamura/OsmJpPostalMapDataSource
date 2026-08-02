package pro.eng.yui.oss.osm.jppostaldata.worker;

import pro.eng.yui.oss.osm.jppostaldata.worker.PrefectureDataJsonGenerator.Result;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

/**
 * 郵便局のリスト (officeList.json) を生成・更新するクラス
 */
public class OfficeListDataGenerator extends AbstDataGenerator {

    /**
     * 指定された生成結果を元に officeList.json を更新する
     * @param results 都道府県ごとの生成結果
     * @throws IOException ファイル入出力エラー
     */
    public void generate(Collection<Result> results) throws IOException {
        Path outputDataDir = Paths.get("pages", "data");
        if (!Files.exists(outputDataDir)) {
            Files.createDirectories(outputDataDir);
        }
        Path officeListPath = outputDataDir.resolve("officeList.json");

        // 既存のリストをロード (ローカル優先、なければリモート)
        JsonNode existing = loadExistingData("data/officeList.json");
        ArrayNode officeList;
        if (existing != null && existing.isArray()) {
            officeList = (ArrayNode) existing;
        } else {
            officeList = mapper.createArrayNode();
        }

        for (Result r : results) {
            final String prefCode = String.format("%02d", r.getPrefCode());
            final String resultTimestampCode;
            if (r.getSubCode() != null) {
                resultTimestampCode = prefCode + "_" + r.getSubCode();
            } else {
                resultTimestampCode = prefCode;
            }

            // 更新対象エリアの既存データを削除 (差分更新のため)
            for (int i = officeList.size() - 1; i >= 0; i--) {
                if (resultTimestampCode.equals(officeList.get(i).path("is_in").asText())) {
                    officeList.remove(i);
                }
            }

            // post_office だけを抽出して追加
            @SuppressWarnings("unchecked")
            List<OsmPoi> pois = (List<OsmPoi>) r.getObjects().get("data");
            if (pois != null) {
                for (OsmPoi poi : pois) {
                    if ("post_office".equals(poi.getTag("amenity"))) {
                        String name = poi.getTag("name");
                        if (name != null && !name.isEmpty()) {
                            ObjectNode office = mapper.createObjectNode();
                            office.put("name", name);
                            office.put("is_in", resultTimestampCode);
                            office.put("poiType", poi.getType());
                            office.put("poiId", poi.getId());
                            officeList.add(office);
                        }
                    }
                }
            }
        }

        // 保存
        simplifiedWriter.writeValue(officeListPath.toFile(), officeList);
        System.out.println("officeList.jsonを更新しました: " + officeListPath.toAbsolutePath());
    }
}
