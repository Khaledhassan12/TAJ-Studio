package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;
import pro.sketchware.ai.data.AiStorage;
import pro.sketchware.ai.search.ConversationSearchSettings;

public class ConversationSearchTool implements Tool {

    @Override
    public ToolSpec spec() {
        return new ToolSpec("search_conversations",
                "Search past conversations for relevant history using keyword or semantic search",
                "{\"query\": \"string\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        String query = args.getString("query");
        if (query == null || query.trim().isEmpty()) {
            return ToolResult.error("query is required");
        }

        Context appContext = ctx.context.getApplicationContext();
        ConversationSearchSettings settings = ConversationSearchSettings.get(appContext);
        if (!settings.isAccessEnabled()) {
            return ToolResult.error("Conversation search is disabled");
        }

        String method = settings.getModelSearchMethod();
        if (method.equals(ConversationSearchSettings.METHOD_SEMANTIC)) {
            // P2-CS2: real semantic path — embed the query with the configured
            // engine (remote or local) and rank stored vectors by cosine.
            ToolResult semantic = searchSemantic(appContext, query, settings);
            if (semantic != null) return semantic;
            return searchKeyword(appContext, query, settings);
        } else {
            return searchKeyword(appContext, query, settings);
        }
    }

    /**
     * @return results IFF semantic search could run; null IFF no embedding
     * model is configured (honest fallback to keyword search).
     */
    private ToolResult searchSemantic(Context context, String query, ConversationSearchSettings settings) {
        List<ConversationSearchSettings.EmbeddingModel> models = settings.getEmbeddingModels();
        if (models.isEmpty()) return null; // Honest fallback: keyword search.

        ConversationSearchSettings.EmbeddingModel model = models.get(0);
        float[] queryVector;
        try {
            pro.sketchware.ai.search.EmbeddingEngine engine =
                    pro.sketchware.ai.search.EmbeddingEngines.forModel(context, model);
            queryVector = engine.embed(java.util.Collections.singletonList(query)).get(0);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "unknown";
            return ToolResult.error("Semantic search failed: " + pro.sketchware.ai.search.EmbeddingEngines.honestError(msg));
        }

        AiStorage storage = AiStorage.get(context);
        float threshold = settings.getSimilarityThreshold();
        int maxResults = settings.getMaxResults();
        String scId = com.besome.sketch.design.DesignActivity.sc_id;

        List<float[]> scores = new ArrayList<>(); // [score, index]
        List<JSONObject> candidates = new ArrayList<>();

        try (Cursor c = storage.listEmbeddings(scId, model.id)) {
            while (c != null && c.moveToNext()) {
                String vectorJson = c.getString(c.getColumnIndexOrThrow("vector"));
                float[] stored = parseVector(vectorJson);
                if (stored == null) continue;
                float score = pro.sketchware.ai.search.EmbeddingEngines.cosine(queryVector, stored);
                if (score < threshold) continue;

                try {
                    JSONObject hit = new JSONObject();
                    hit.put("message_id", c.getString(c.getColumnIndexOrThrow("messageId")));
                    hit.put("score", Math.round(score * 1000f) / 1000f);
                    String text = c.getString(c.getColumnIndexOrThrow("text"));
                    if (text != null) hit.put("content", text);
                    candidates.add(hit);
                    scores.add(new float[]{score, candidates.size() - 1});
                } catch (Exception ignored) {}
            }
        }

        if (candidates.isEmpty()) {
            return ToolResult.success("{\"results\":[],\"count\":0,\"method\":\"semantic\",\"note\":\"no stored vectors scored >= " + threshold + "\"}");
        }

        // Rank by score descending, keep top maxResults.
        java.util.Collections.sort(scores, (a, b) -> Float.compare(b[0], a[0]));
        JSONArray results = new JSONArray();
        try {
            for (int i = 0; i < scores.size() && results.length() < maxResults; i++) {
                JSONObject hit = candidates.get((int) scores.get(i)[1]);
                // Enrich with message metadata when the row has no text yet.
                if (!hit.has("content")) {
                    enrichFromMessages(storage, hit);
                }
                results.put(hit);
            }

            JSONObject out = new JSONObject();
            out.put("results", results);
            out.put("count", results.length());
            out.put("method", "semantic");
            out.put("engine", model.type);
            return ToolResult.success(out.toString());
        } catch (Exception e) {
            return ToolResult.error("Semantic search failed: " + e.getMessage());
        }
    }

    private float[] parseVector(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONArray arr = new JSONArray(json);
            float[] vector = new float[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                vector[i] = (float) arr.getDouble(i);
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }

    private void enrichFromMessages(AiStorage storage, JSONObject hit) {
        try {
            String messageId = hit.getString("message_id");
            try (Cursor mc = storage.findMessageById(messageId)) {
                if (mc != null && mc.moveToFirst()) {
                    hit.put("role", mc.getString(mc.getColumnIndexOrThrow("role")));
                    hit.put("content", mc.getString(mc.getColumnIndexOrThrow("content")));
                    hit.put("conversation_id", mc.getString(mc.getColumnIndexOrThrow("conversationId")));
                }
            }
        } catch (Exception ignored) {}
    }

    private ToolResult searchKeyword(Context context, String query, ConversationSearchSettings settings) {
        AiStorage storage = AiStorage.get(context);
        int maxResults = settings.getMaxResults();
        int contextSize = settings.getContextPerHit();

        JSONArray results = new JSONArray();
        try {
            // This is a simplified keyword search. 
            // In a real implementation, we would use FTS or indexed queries.
            // Here we iterate and match titles/messages for the current project.
            
            String scId = com.besome.sketch.design.DesignActivity.sc_id;
            try (Cursor c = storage.listConversations(scId)) {
                while (c != null && c.moveToNext() && results.length() < maxResults) {
                    String convId = c.getString(c.getColumnIndexOrThrow("id"));
                    String title = c.getString(c.getColumnIndexOrThrow("title"));
                    
                    boolean match = title != null && title.toLowerCase().contains(query.toLowerCase());
                    
                    JSONArray matchedMessages = new JSONArray();
                    List<JSONObject> allMessages = new ArrayList<>();
                    
                    try (Cursor mc = storage.listMessages(convId)) {
                        while (mc != null && mc.moveToNext()) {
                            JSONObject m = new JSONObject();
                            m.put("role", mc.getString(mc.getColumnIndexOrThrow("role")));
                            m.put("content", mc.getString(mc.getColumnIndexOrThrow("content")));
                            allMessages.add(m);
                            
                            if (m.getString("content").toLowerCase().contains(query.toLowerCase())) {
                                match = true;
                            }
                        }
                    }
                    
                    if (match) {
                        JSONObject hit = new JSONObject();
                        hit.put("conversation_id", convId);
                        hit.put("title", title);
                        
                        // Extract context around the first match or just last N messages
                        JSONArray contextArray = new JSONArray();
                        int start = Math.max(0, allMessages.size() - contextSize);
                        for (int i = start; i < allMessages.size(); i++) {
                            contextArray.put(allMessages.get(i));
                        }
                        hit.put("messages", contextArray);
                        results.put(hit);
                    }
                }
            }

            JSONObject out = new JSONObject();
            out.put("results", results);
            out.put("count", results.length());
            return ToolResult.success(out.toString());

        } catch (Exception e) {
            return ToolResult.error("Search failed: " + e.getMessage());
        }
    }
}
