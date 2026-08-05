package pro.eng.yui.oss.osm.jppostaldata.worker;

import pro.eng.yui.oss.osm.jppostaldata.Main;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.api.overpass.OverpassQuery;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

public class PrefectureDataJsonGenerator {

    public PrefectureDataJsonGenerator() {
    }
    
    public static class Result{
        private final int code;
        public int getPrefCode(){ return code; }
        private final String subCode;
        public String getSubCode(){ return subCode; }
        private final String name;
        public String getPrefName(){ return name; }
        private final String kana;
        public String getPrefKana(){ return kana; }

        private final LocalDateTime dataTimestamp;
        public LocalDateTime getDataTimestamp() { return dataTimestamp; }

        private final LocalDateTime lastLoaded;
        public LocalDateTime getLastLoaded() { return lastLoaded; }

        private final Map<String, Object> data;
        public Map<String, Object> getObjects(){ return data; }
        public int getDataSize(){
            Object dataList = data.get("data");
            if (dataList instanceof List) {
                return ((List<?>) dataList).size();
            }
            return 0;
        }

        public Result(int prefCode, String prefName, String kana, LocalDateTime timestamp, Map<String, Object> jsonData) {
            this(prefCode, null, prefName, kana, timestamp, timestamp, jsonData);
        }

        public Result(int prefCode, String subCode, String prefName, String kana, LocalDateTime timestamp, Map<String, Object> jsonData) {
            this(prefCode, subCode, prefName, kana, timestamp, timestamp, jsonData);
        }

        public Result(int prefCode, String subCode, String prefName, String kana, LocalDateTime lastModified, LocalDateTime lastLoaded, Map<String, Object> jsonData) {
            this.code = prefCode;
            this.subCode = subCode;
            this.name = prefName;
            this.kana = kana;
            this.dataTimestamp = lastModified;
            this.lastLoaded = lastLoaded;
            this.data = jsonData;
        }
    }
    
    public static class ResultTimestamp{
        public final String code;
        public final String name;
        public final String kana;
        public final String lastModified;
        public final String lastLoaded;
        public final int objectCount;
        public ResultTimestamp(int prefCode, String prefName, String kana, LocalDateTime modified, LocalDateTime loaded, int objectCount){
            this(String.format("%02d",prefCode), prefName, kana, modified, loaded, objectCount);
        }
        public ResultTimestamp(String code, String prefName, String kana, LocalDateTime modified, LocalDateTime loaded, int objectCount){
            this.code = code;
            this.name = prefName;
            this.kana = kana;
            this.lastModified = Main.FORMATTER.format(modified);
            this.lastLoaded = Main.FORMATTER.format(loaded);
            this.objectCount = objectCount;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this){ return true; }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            ResultTimestamp that = (ResultTimestamp) obj;
            return Objects.equals(code, that.code) && name.equals(that.name) &&
                    Objects.equals(kana, that.kana) &&
                    lastModified.equals(that.lastModified) &&
                    lastLoaded.equals(that.lastLoaded) &&
                    objectCount == that.objectCount;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + code.hashCode();
            result = 31 * result + Objects.hashCode(kana);
            result = 31 * result + lastModified.hashCode();
            result = 31 * result + lastLoaded.hashCode();
            result = 31 * result + Integer.hashCode(objectCount);
            return result;
        }
    }

    /** 
     * @param prefCode 都道府県コード
     * @param prefName 都道府県名
     * @param prefKana 都道府県よみがな */
    public Result generate(int prefCode, String prefName, String prefKana) throws IOException {
        return generate(prefCode, null, 4, prefName, prefKana);
    }

    /**
     * @param prefCode 都道府県コード
     * @param subCode サブエリアコード (null可)
     * @param adminLevel 行政レベル
     * @param areaName エリア名
     * @param areaKana エリアよみがな */
    public Result generate(int prefCode, String subCode, int adminLevel, String areaName, String areaKana) throws IOException {
        Map<String, Object> data = new HashMap<>();

        String query = OverpassQuery.getPostSearchQuery(adminLevel, areaName);
        try {
            List<OsmPoi> pois = JpPostalUtil.callOverpass(query, 5, 45, 180).join();

            LocalDateTime timestamp = LocalDateTime.now(Main.JST);
            data.put("lastModified", timestamp.format(Main.FORMATTER));
            data.put("lastLoaded", timestamp.format(Main.FORMATTER));
            data.put("prefectureCode", prefCode);
            if (subCode != null) {
                data.put("subCode", subCode);
            }
            data.put("prefectureName", areaName);
            data.put("prefectureKana", areaKana);
            data.put("data", pois);

            return new Result(prefCode, subCode, areaName, areaKana, timestamp, data);
        }catch (CompletionException e) {
            throw new IOException(e);
        }
    }
}

