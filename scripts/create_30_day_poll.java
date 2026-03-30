///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

class create_30_day_poll {
    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8088";
        String payload = buildPayload();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/polls"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Poll creation failed with status " + response.statusCode() + " and body: " + response.body());
        }

        System.out.println(response.body());
    }

    private static String buildPayload() {
        StringBuilder dates = new StringBuilder();
        for (int day = 1; day <= 30; day++) {
            if (day > 1) {
                dates.append(',');
            }
            dates.append('"').append(LocalDate.of(2026, 3, day)).append('"');
        }

        return """
                {
                  "authorName": "Codex Script",
                  "authorEmail": "codex@example.com",
                  "title": "März 2026 mit 30 Tagen",
                  "description": "Automatisch erzeugte Ganztags-Umfrage mit 30 Einträgen.",
                  "eventType": "ALL_DAY",
                  "durationMinutes": null,
                  "dates": [%s],
                  "startTimes": [],
                  "expiresAtOverride": null
                }
                """.formatted(dates);
    }
}
