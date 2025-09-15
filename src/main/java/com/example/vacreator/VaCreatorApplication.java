package com.example.vacreator;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class VaCreatorApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(VaCreatorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        if (args == null || args.length < 4) {
            printUsage();
            return;
        }

        String token = args[0];
        String command = args[1].toUpperCase();
        String analyticsType = args[args.length - 1].toUpperCase();

        if (!"OD".equals(analyticsType) && !"SVA".equals(analyticsType)) {
            System.err.println("Unrecognized analytics type: " + analyticsType);
            printUsage();
            return;
        }

        String[] baseArgs = Arrays.copyOf(args, args.length - 1);

        List<Integer> streamIds;
        try {
            if ("FROM".equals(command)) {
                streamIds = parseRangeArgs(baseArgs);
            } else if ("FOR".equals(command)) {
                streamIds = parseForArgs(baseArgs);
            } else {
                System.err.println("Unrecognized command: " + args[1]);
                printUsage();
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            printUsage();
            return;
        }

        if (streamIds.isEmpty()) {
            System.err.println("No stream identifiers provided.");
            return;
        }

        VaCreatorApplication app = new VaCreatorApplication();
        for (Integer id : streamIds) {
            try {
                app.createAnalytics(token, id, analyticsType);
            } catch (IOException | InterruptedException e) {
                System.err.println("Failed to create analytics for stream " + id + ": " + e.getMessage());
            }
        }
    }

    private static List<Integer> parseRangeArgs(String[] args) {
        // Expect pattern: <token> FROM x TO y
        if (args.length < 5) {
            throw new IllegalArgumentException("Missing arguments for FROM/TO range.");
        }
        // args[2] should be the start of the range, args[4] should be the end
        int start;
        int end;
        try {
            start = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid start value in range: " + args[2]);
        }
        // The literal "TO" can appear at args[3]; if so, pick args[4] as end
        int endIndex;
        if (args[3].equalsIgnoreCase("TO")) {
            endIndex = 4;
        } else {
            // Otherwise we assume pattern is <token> FROM x y
            endIndex = 3;
        }
        if (endIndex >= args.length) {
            throw new IllegalArgumentException("Missing end value in range.");
        }
        try {
            end = Integer.parseInt(args[endIndex]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid end value in range: " + args[endIndex]);
        }
        // Determine correct order if reversed
        int from = Math.min(start, end);
        int to = Math.max(start, end);
        List<Integer> ids = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            ids.add(i);
        }
        return ids;
    }

    /**
     * Parses command line arguments for the "FOR [x, y, z...]" syntax.
     *
     * @param args the raw command line arguments
     * @return a list of integers extracted from the bracketed list
     * @throws IllegalArgumentException if the argument list is malformed
     */
    private static List<Integer> parseForArgs(String[] args) {
        // Everything after the "FOR" keyword is considered part of the list.
        if (args.length < 3) {
            throw new IllegalArgumentException("Missing list for FOR command.");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            builder.append(args[i]);
            if (i < args.length - 1) {
                builder.append(" ");
            }
        }
        String combined = builder.toString().trim();
        // Remove square brackets if present
        combined = combined.replaceAll("^\\[", "").replaceAll("\\]$", "");
        // Remove spaces around commas
        combined = combined.replaceAll("\\s*,\\s*", ",");
        if (combined.isEmpty()) {
            throw new IllegalArgumentException("No identifiers provided in list.");
        }
        String[] parts = combined.split(",");
        List<Integer> ids = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                ids.add(Integer.parseInt(part));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid identifier in list: " + part);
            }
        }
        return ids;
    }

    /**
     * Sends a POST request to create an analytics configuration of the specified type
     * for the given stream identifier.
     *
     * @param token         the bearer token used for authentication
     * @param streamId      the stream identifier for which the analytics should be created
     * @param analyticsType the type of analytics to create ("SVA" or "OD")
     * @throws IOException          if an I/O error occurs when sending or receiving
     *                              the request
     * @throws InterruptedException if the operation is interrupted
     */
    public void createAnalytics(String token, int streamId, String analyticsType) throws IOException, InterruptedException {
        String url;
        String json;

        if ("SVA".equalsIgnoreCase(analyticsType)) {
            url = "http://localhost:2001/api/v2/smart_va/analytics";
            json = "{" +
                    "\"type\":\"smart_va\"," +
                    "\"stream_id\":" + streamId + "," +
                    "\"allowed_server_ids\":null," +
                    "\"module\":{" +
                    "\"advanced_settings\":{" +
                    "\"tracker\":\"normal\"," +
                    "\"sensitivity\":5," +
                    "\"model\":\"performance\"," +
                    "\"tracker_buffer_time\":10," +
                    "\"min_width\":25," +
                    "\"min_height\":25" +
                    "}," +
                    "\"hardware_settings\":{" +
                    "\"acceleration\":\"\"," +
                    "\"decoding\":\"nvidia\"," +
                    "\"hardware\":\"gpu\"," +
                    "\"frame_rate_settings\":{" +
                    "\"mode\":\"fps\"," +
                    "\"fps\":\"5\"" +
                    "}," +
                    "\"motion\":false" +
                    "}," +
                    "\"mode\":\"alert\"," +
                    "\"rules\":[]" +
                    "}," +
                    "\"events_holder\":{" +
                    "\"notify_enabled\":false," +
                    "\"events\":[]" +
                    "}," +
                    "\"access_restrictions\":{" +
                    "\"role_permissions\":{}," +
                    "\"user_permissions\":{}," +
                    "\"default_permissions\":{" +
                    "\"StartAnalytics\":true," +
                    "\"StopAnalytics\":true," +
                    "\"EditAnalytics\":true," +
                    "\"ViewAnalyticsLive\":true," +
                    "\"ViewAnalyticsEvents\":true" +
                    "}" +
                    "}" +
                    "}";
        } else { // OD
            url = "http://localhost:2001/api/v2/object_in_zone/analytics";
            json = "{" +
                    "\"type\":\"object_in_zone\"," +
                    "\"stream_id\":" + streamId + "," +
                    "\"allowed_server_ids\":null," +
                    "\"module\":{" +
                    "\"advanced_settings\":{" +
                    "\"tracker\":\"normal\"," +
                    "\"alarm_filtration\":true," +
                    "\"sensitivity\":5," +
                    "\"model\":\"quality\"," +
                    "\"tracker_buffer_time\":20," +
                    "\"min_width\":25," +
                    "\"min_height\":25" +
                    "}," +
                    "\"hardware_settings\":{" +
                    "\"acceleration\":\"\"," +
                    "\"decoding\":\"nvidia\"," +
                    "\"hardware\":\"gpu\"," +
                    "\"frame_rate_settings\":{" +
                    "\"mode\":\"fps\"," +
                    "\"fps\":\"10\"" +
                    "}," +
                    "\"motion\":false" +
                    "}," +
                    "\"excluded_crossing\":8," +
                    "\"mode\":\"alert\"," +
                    "\"zone_crossing\":8," +
                    "\"polygons\":[{" +
                    "\"points\":[" +
                    "{\"x\":\"0.0078\",\"y\":\"0.0139\"}," +
                    "{\"x\":\"0.9922\",\"y\":\"0.0139\"}," +
                    "{\"x\":\"0.9922\",\"y\":\"0.9861\"}," +
                    "{\"x\":\"0.0078\",\"y\":\"0.9861\"}" +
                    "]," +
                    "\"types\":[\"0\"]," +
                    "\"time_periods\":[{" +
                    "\"start_time\":\"00:00:00\"," +
                    "\"end_time\":\"23:59:59\"," +
                    "\"trigger\":6," +
                    "\"dwell_time\":\"5\"," +
                    "\"object_counter_limit\":\"1\"," +
                    "\"selected_days\":[0,1,2,3,4,5,6]" +
                    "}]," +
                    "\"color\":\"#A347FF\"," +
                    "\"name\":\"Rule 1\"," +
                    "\"excluded\":[]" +
                    "}]" +
                    "}," +
                    "\"events_holder\":{" +
                    "\"notify_enabled\":0," +
                    "\"events\":[]" +
                    "}," +
                    "\"access_restrictions\":{" +
                    "\"role_permissions\":{}," +
                    "\"user_permissions\":{}," +
                    "\"default_permissions\":{" +
                    "\"StartAnalytics\":true," +
                    "\"StopAnalytics\":true," +
                    "\"EditAnalytics\":true," +
                    "\"ViewAnalyticsLive\":true," +
                    "\"ViewAnalyticsEvents\":true" +
                    "}" +
                    "}" +
                    "}";
        }
        // Build the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            System.out.println("Successfully created analytics for stream " + streamId + ".");
        } else {
            System.err.println("Failed to create analytics for stream " + streamId + ". HTTP status: " + statusCode);
            System.err.println("Response body: " + response.body());
        }
    }

    /**
     * Prints usage instructions to standard output.
     */
    private static void printUsage() {
        System.out.println("Usage: java -jar vaCreator.jar <token> FROM <start> TO <end> <type>");
        System.out.println("   or: java -jar vaCreator.jar <token> FOR [id1,id2,id3,...] <type>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  FROM <start> TO <end>   Creates analytics for all stream IDs from <start> to <end>, inclusive.");
        System.out.println("  FOR [list]              Creates analytics for each stream ID in the comma-separated list. Spaces are allowed.");
        System.out.println();
        System.out.println("<type> must be 'od' for Object Detection or 'sva' for Smart VA.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar vaCreator.jar abc123 FROM 10 TO 15 od");
        System.out.println("  java -jar vaCreator.jar abc123 FOR [2, 5, 8] sva");
    }
}
