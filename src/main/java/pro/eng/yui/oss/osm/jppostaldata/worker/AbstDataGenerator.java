package pro.eng.yui.oss.osm.jppostaldata.worker;

import pro.eng.yui.oss.osm.jppostaldata.Main;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

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
import java.util.concurrent.TimeUnit;

public abstract class AbstDataGenerator {
    
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final ObjectWriter simplifiedWriter;
    protected final HttpClient httpClient = HttpClient.newBuilder().build();

    protected AbstDataGenerator() {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultPrettyPrinter.FixedSpaceIndenter())
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        simplifiedWriter = mapper.writer().with(pp);
    }

    protected JsonNode loadPrefJson() throws IOException {
        try (InputStream is = Main.class.getResourceAsStream("/content/master/pref.json")) {
            if (is == null) return null;
            return mapper.readTree(is);
        }
    }

    protected JsonNode loadExistingData(String relativePath) {
        // 1. Check local
        Path localPath = Paths.get("pages", relativePath);
        if (Files.exists(localPath)) {
            try {
                JsonNode node = mapper.readTree(localPath.toFile());
                System.out.println("Loaded existing data from local: " + localPath.toAbsolutePath());
                return node;
            } catch (Exception e) {
                System.err.println("Failed to read local " + relativePath + ": " + e.getMessage());
            }
        }

        // 2. Check remote
        try {
            String remoteUrl = "https://yui-kitamura.github.io/OsmJpPostalMapDataSource/" + relativePath;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteUrl))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = mapper.readTree(response.body());
                System.out.println("Loaded existing data from remote: " + remoteUrl);
                return node;
            } else if (response.statusCode() != 404) {
                System.err.println("Failed to fetch remote " + relativePath + ". Status: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Error fetching remote " + relativePath + ": " + e.getMessage());
        }
        return null;
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
