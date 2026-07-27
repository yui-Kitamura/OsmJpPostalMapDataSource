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
        private final String name;
        public String getPrefName(){ return name; }

        private final LocalDateTime dataTimestamp;
        public LocalDateTime getDataTimestamp() { return dataTimestamp; }

        private final Map<String, Object> data;
        public Map<String, Object> getObjects(){ return data; }
        public int getDataSize(){
            return ((List<OsmPoi>)data.get("data")).size();
        }

        public Result(int prefCode, String prefName, LocalDateTime timestamp, Map<String, Object> jsonData) {
            this.code = prefCode;
            this.name = prefName;
            this.dataTimestamp = timestamp;
            this.data = jsonData;
        }
    }
    
    public static class ResultTimestamp{
        public final String code;
        public final String name;
        public final String lastModified;
        public final int objectCount;
        public ResultTimestamp(int prefCode, String prefName, LocalDateTime time, int objectCount){
            this.code = String.format("%02d",prefCode);
            this.name = prefName;
            lastModified = Main.FORMATTER.format(time);
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
                    lastModified.equals(that.lastModified) && objectCount == that.objectCount;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + code.hashCode();
            result = 31 * result + lastModified.hashCode();
            result = 31 * result + Integer.hashCode(objectCount);
            return result;
        }
    }

    /** 
     * @param prefCode 都道府県コード
     * @param prefName 都道府県名 */
    public Result generate(int prefCode, String prefName) throws IOException {
        Map<String, Object> data = new HashMap<>();

        String query = OverpassQuery.getPostSearchQuery(prefName);
        try {
            List<OsmPoi> pois = JpPostalUtil.callOverpass(query, 5, 30, 120).join();

            LocalDateTime timestamp = LocalDateTime.now(Main.JST);
            data.put("lastModified", timestamp.format(Main.FORMATTER));
            data.put("prefectureCode", prefCode);
            data.put("prefectureName", prefName);
            data.put("data", pois);

            return new Result(prefCode, prefName, timestamp, data);
        }catch (CompletionException e) {
            throw new IOException(e);
        }
    }
}

