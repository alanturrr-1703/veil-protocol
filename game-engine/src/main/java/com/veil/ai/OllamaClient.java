package com.veil.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin client for a local Ollama server. Used only to add natural-language FLAVOR to
 * an answer whose FACTS were already determined deterministically from NPC memory —
 * the model never decides what is true, so it cannot make an NPC "lie".
 */
public class OllamaClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final String baseUrl;
    private final String model;

    public OllamaClient() {
        this("http://localhost:11434", "llama3");
    }

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    /** Returns the generated text, or the fallback if the model is unreachable. */
    public String generate(String prompt, String fallback) {
        try {
            String body = "{\"model\":\"" + model + "\",\"prompt\":\"" + escape(prompt)
                    + "\",\"stream\":false}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String text = extractResponseField(response.body());
            return text == null || text.isBlank() ? fallback : text;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** Naive extraction of the "response" field to avoid a JSON dependency. */
    private static String extractResponseField(String json) {
        if (json == null) return null;
        String key = "\"response\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                sb.append(next == 'n' ? '\n' : next);
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
