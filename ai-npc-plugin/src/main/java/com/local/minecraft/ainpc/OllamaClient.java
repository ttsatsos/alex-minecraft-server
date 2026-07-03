package com.local.minecraft.ainpc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OllamaClient {
    private final HttpClient httpClient;
    private final URI chatUri;
    private final String defaultModel;
    private final String systemPrompt;

    public OllamaClient(String baseUrl, String defaultModel, int timeoutSeconds, String systemPrompt) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.chatUri = URI.create(baseUrl.replaceAll("/+$", "") + "/api/chat");
        this.defaultModel = defaultModel;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public String chat(String modelOverride, JSONArray messages) throws IOException, InterruptedException {
        JSONObject payload = new JSONObject();
        payload.put("model", modelOverride == null || modelOverride.isBlank() ? defaultModel : modelOverride);
        payload.put("stream", false);
        payload.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(chatUri)
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Ollama returned " + response.statusCode() + ": " + response.body());
        }

        JSONObject body = new JSONObject(response.body());
        if (!body.has("message")) {
            throw new IOException("Ollama response did not contain a message field.");
        }

        return body.getJSONObject("message").optString("content", "").trim();
    }
}
