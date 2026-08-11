package pro.sketchware.ai.search;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * [WHAT] Remote (OpenAI-protocol) embedding engine (P2-CS2).
 * [WHY] Preserves the existing P2-CS HTTP flow, now batch-capable and
 * surfaced through the unified EmbeddingEngine interface.
 * [HOW] POST {baseUrl}/embeddings with {model, input:[...]}; key comes from
 * SecureKeyStore via ConversationSearchSettings (RISK-4: never logged).
 */
public class RemoteEmbeddingEngine implements EmbeddingEngine {

    private final ConversationSearchSettings.EmbeddingModel model;
    private final String apiKey;

    public RemoteEmbeddingEngine(ConversationSearchSettings.EmbeddingModel model, String apiKey) {
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public List<float[]> embed(List<String> texts) throws Exception {
        if (model.baseUrl == null || model.baseUrl.isEmpty()) {
            throw new Exception("No base URL configured for remote embedding model '" + model.name + "'");
        }

        String endpoint = model.baseUrl.endsWith("/")
                ? model.baseUrl.substring(0, model.baseUrl.length() - 1) + "/embeddings"
                : model.baseUrl + "/embeddings";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("model", model.modelName);
        JSONArray input = new JSONArray();
        for (String t : texts) input.put(t);
        body.put("input", input);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            if (code == 401 || code == 403) {
                throw new Exception("Invalid API key for embedding model '" + model.name + "' (HTTP " + code + ")");
            }
            if (code == 429) {
                throw new Exception("Rate limited by embedding provider (HTTP 429)");
            }
            throw new Exception("Remote embedding failed (HTTP " + code + ")");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        } finally {
            conn.disconnect();
        }

        JSONObject res = new JSONObject(sb.toString());
        JSONArray data = res.optJSONArray("data");
        if (data == null || data.length() < texts.size()) {
            throw new Exception("Embedding provider returned fewer vectors than inputs");
        }

        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            JSONArray embedding = data.getJSONObject(i).getJSONArray("embedding");
            float[] vector = new float[embedding.length()];
            for (int j = 0; j < embedding.length(); j++) {
                vector[j] = (float) embedding.getDouble(j);
            }
            vectors.add(vector);
        }
        return vectors;
    }
}
