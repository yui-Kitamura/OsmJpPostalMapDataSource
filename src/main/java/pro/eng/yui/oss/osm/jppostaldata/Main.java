package pro.eng.yui.oss.osm.jppostaldata;

import pro.eng.yui.oss.osm.jppostaldata.worker.BoundaryDataGenerator;
import pro.eng.yui.oss.osm.jppostaldata.worker.CityAndSuburbDataGenerator;
import pro.eng.yui.oss.osm.jppostaldata.worker.PostalDataGenerator;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    
    public static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("y/MM/dd'T'HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("定期実行開始: " + FORMATTER.format(ZonedDateTime.now(JST)));

        boolean runBoundary = false;
        boolean runCity = false;
        String targetPrefecture = null;

        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if ("--boundary".equals(arg)) {
                runBoundary = true;
            } else if ("--city".equals(arg)) {
                runCity = true;
            } else {
                if (targetPrefecture != null) {
                    throw new IllegalArgumentException("引数には都道府県コードまたは都道府県名を1つだけ指定してください");
                }
                targetPrefecture = arg.trim();
            }
        }

        if (runBoundary) {
            try {
                new BoundaryDataGenerator().generate();
            } catch (IOException e) {
                System.err.println("boundary.jsonの生成に失敗しました");
                e.printStackTrace();
            }
        }

        if (runCity) {
            try {
                new CityAndSuburbDataGenerator().generate(targetPrefecture);
            } catch (IOException e) {
                System.err.println("cityAndSuburb.jsonの生成に失敗しました");
                e.printStackTrace();
            }
        }

        try {
            new PostalDataGenerator().generate(targetPrefecture);
        } catch (IOException e) {
            System.err.println("ファイルの生成に失敗しました");
            e.printStackTrace();
        }

        System.out.println("定期実行終了: " + FORMATTER.format(ZonedDateTime.now(JST)));
        
    }
}
