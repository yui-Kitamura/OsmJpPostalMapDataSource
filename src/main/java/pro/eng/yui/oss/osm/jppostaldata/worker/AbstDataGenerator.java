package pro.eng.yui.oss.osm.jppostaldata.worker;

import pro.eng.yui.oss.osm.jppostaldata.Main;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public abstract class AbstDataGenerator {

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final HttpClient httpClient = HttpClient.newBuilder().build();

    protected JsonNode loadPrefJson() throws IOException {
        try (InputStream is = Main.class.getResourceAsStream("/content/master/pref.json")) {
            if (is == null) return null;
            return mapper.readTree(is);
        }
    }

    protected JsonNode executeOverpassQuery(String query) throws Exception {
        int maxRetry = 3;
        int interval = 8;

        for (int i = 0; i < maxRetry; i++) {
            try {
                String form = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://overpass-api.de/api/interpreter"))
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return mapper.readTree(response.body());
                } else if (response.statusCode() == 429) {
                    interval += 10;
                    System.out.println("Overpass API 429. Retrying in " + interval + "s...");
                    TimeUnit.SECONDS.sleep(interval);
                } else if (response.statusCode() == 504) {
                    System.out.println("Overpass API 504. Retrying in " + interval + "s...");
                    TimeUnit.SECONDS.sleep(interval);
                } else {
                    throw new IOException("Overpass API error: " + response.statusCode());
                }
            } catch (Exception e) {
                if (i == maxRetry - 1) {
                    System.err.println("Overpass API error after " + maxRetry + " attempts: " + e.getMessage());
                    throw e;
                }
                TimeUnit.SECONDS.sleep(interval);
            }
        }
        System.err.println("Overpass API failed to return data after " + maxRetry + " attempts.");
        return null;
    }
}
