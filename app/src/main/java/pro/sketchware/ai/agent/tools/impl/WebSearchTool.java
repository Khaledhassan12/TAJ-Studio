package pro.sketchware.ai.agent.tools.impl;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import pro.sketchware.ai.agent.tools.Tool;
import pro.sketchware.ai.agent.tools.ToolArgs;
import pro.sketchware.ai.agent.tools.ToolCtx;
import pro.sketchware.ai.agent.tools.ToolResult;
import pro.sketchware.ai.agent.tools.ToolSpec;
import pro.sketchware.ai.websearch.WebSearchSettings;

/**
 * [WHAT] Agent tool "web_search": searches the web via one of 6 providers.
 * [WHY] P2-WS: real wiring per §11 (no fake UI); honest errors per R7.
 * [HOW] Runs on background thread (AgentManager.executeToolCall); 10s connect,
 * 10s read timeouts. Returns JSON {"results":[{"title","url","snippet"}],"count":N}
 * or {"error":"<honest message>"}.
 *
 * [العربية]
 * أداة الوكيل "web_search": تبحث في الويب عبر أحد المزودين الستة.
 * تعيد نتائج JSON أو رسالة خطأ صادقة.
 */
public class WebSearchTool implements Tool {

    private static final String TAG = "WebSearchTool";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("web_search",
                "Search the web for real-time information; returns a list of results with title, url, and snippet",
                "{\"query\": \"string\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        String query = args.getString("query");
        if (query == null || query.trim().isEmpty()) {
            return ToolResult.error("query is required");
        }

        Context appContext = ctx.context.getApplicationContext();
        WebSearchSettings settings = WebSearchSettings.get(appContext);
        if (!settings.isEnabled()) {
            return ToolResult.error("Web Search is disabled");
        }

        String provider = settings.getProvider();
        int numResults = settings.getNumResults();

        try {
            String json;
            switch (provider) {
                case WebSearchSettings.PROVIDER_BRAVE:
                    json = searchBrave(query, numResults, settings);
                    break;
                case WebSearchSettings.PROVIDER_KAGI:
                    json = searchKagi(query, numResults, settings);
                    break;
                case WebSearchSettings.PROVIDER_SERPER:
                    json = searchSerper(query, numResults, settings);
                    break;
                case WebSearchSettings.PROVIDER_TAVILY:
                    json = searchTavily(query, numResults, settings);
                    break;
                case WebSearchSettings.PROVIDER_SEARXNG:
                    json = searchSearxng(query, numResults, settings);
                    break;
                case WebSearchSettings.PROVIDER_DUCKDUCKGO:
                    json = searchDuckDuckGo(query, numResults);
                    break;
                default:
                    return ToolResult.error("Unknown provider: " + provider);
            }
            return ToolResult.success(json);
        } catch (Exception e) {
            Log.e(TAG, "web_search failed", e);
            return ToolResult.error("Web search failed: " + e.getMessage());
        }
    }

    // --- Brave ---

    private String searchBrave(String query, int numResults, WebSearchSettings settings) throws Exception {
        String apiKey = settings.getKey(WebSearchSettings.PROVIDER_BRAVE);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("API key required for Brave");
        }

        String encoded = URLEncoder.encode(query, "UTF-8");
        String urlStr = "https://api.search.brave.com/res/v1/web/search?q=" + encoded + "&count=" + numResults;

        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestProperty("X-Subscription-Token", apiKey);
        conn.setRequestProperty("Accept", "application/json");

        String response = readResponse(conn);
        int code = conn.getResponseCode();
        conn.disconnect();

        if (code != 200) {
            throw new IOException("HTTP " + code);
        }

        JSONObject obj = new JSONObject(response);
        JSONArray webResults = obj.optJSONArray("web");
        if (webResults == null) {
            webResults = obj.optJSONArray("results");
        }
        if (webResults == null) {
            webResults = new JSONArray();
        }

        JSONArray results = new JSONArray();
        int limit = Math.min(numResults, webResults.length());
        for (int i = 0; i < limit; i++) {
            JSONObject r = webResults.getJSONObject(i);
            JSONObject item = new JSONObject();
            item.put("title", r.optString("title", ""));
            item.put("url", r.optString("url", ""));
            item.put("snippet", r.optString("description", ""));
            results.put(item);
        }

        JSONObject out = new JSONObject();
        out.put("results", results);
        out.put("count", results.length());
        return out.toString();
    }

    // --- Kagi ---

    private String searchKagi(String query, int numResults, WebSearchSettings settings) throws Exception {
        String apiKey = settings.getKey(WebSearchSettings.PROVIDER_KAGI);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("API key required for Kagi");
        }

        String encoded = URLEncoder.encode(query, "UTF-8");
        String urlStr = "https://kagi.com/api/v0/search?q=" + encoded + "&limit=" + numResults;

        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestProperty("Authorization", "Bot " + apiKey);
        conn.setRequestProperty("Accept", "application/json");

        String response = readResponse(conn);
        int code = conn.getResponseCode();
        conn.disconnect();

        if (code != 200) {
            throw new IOException("HTTP " + code);
        }

        JSONObject obj = new JSONObject(response);
        JSONObject data = obj.optJSONObject("data");
        if (data == null) {
            throw new IOException("Invalid Kagi response");
        }
        JSONArray webResults = data.optJSONArray("web");
        if (webResults == null) {
            webResults = new JSONArray();
        }

        JSONArray results = new JSONArray();
        int limit = Math.min(numResults, webResults.length());
        for (int i = 0; i < limit; i++) {
            JSONObject r = webResults.getJSONObject(i);
            JSONObject item = new JSONObject();
            item.put("title", r.optString("title", ""));
            item.put("url", r.optString("url", ""));
            item.put("snippet", r.optString("snippet", ""));
            results.put(item);
        }

        JSONObject out = new JSONObject();
        out.put("results", results);
        out.put("count", results.length());
        return out.toString();
    }

    // --- Serper ---

    private String searchSerper(String query, int numResults, WebSearchSettings settings) throws Exception {
        String apiKey = settings.getKey(WebSearchSettings.PROVIDER_SERPER);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("API key required for Serper");
        }

        String urlStr = "https://google.serper.dev/search";

        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-API-KEY", apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("q", query);
        body.put("num", numResults);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        String response = readResponse(conn);
        int code = conn.getResponseCode();
        conn.disconnect();

        if (code != 200) {
            throw new IOException("HTTP " + code);
        }

        JSONObject obj = new JSONObject(response);
        JSONArray organic = obj.optJSONArray("organic");
        if (organic == null) {
            organic = new JSONArray();
        }

        JSONArray results = new JSONArray();
        int limit = Math.min(numResults, organic.length());
        for (int i = 0; i < limit; i++) {
            JSONObject r = organic.getJSONObject(i);
            JSONObject item = new JSONObject();
            item.put("title", r.optString("title", ""));
            item.put("url", r.optString("link", ""));
            item.put("snippet", r.optString("snippet", ""));
            results.put(item);
        }

        JSONObject out = new JSONObject();
        out.put("results", results);
        out.put("count", results.length());
        return out.toString();
    }

    // --- Tavily ---

    private String searchTavily(String query, int numResults, WebSearchSettings settings) throws Exception {
        String apiKey = settings.getKey(WebSearchSettings.PROVIDER_TAVILY);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("API key required for Tavily");
        }

        String urlStr = "https://api.tavily.com/search";

        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("api_key", apiKey);
        body.put("query", query);
        body.put("max_results", numResults);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        String response = readResponse(conn);
        int code = conn.getResponseCode();
        conn.disconnect();

        if (code != 200) {
            throw new IOException("HTTP " + code);
        }

        JSONObject obj = new JSONObject(response);
        JSONArray resultsArray = obj.optJSONArray("results");
        if (resultsArray == null) {
            resultsArray = new JSONArray();
        }

        JSONArray results = new JSONArray();
        int limit = Math.min(numResults, resultsArray.length());
        for (int i = 0; i < limit; i++) {
            JSONObject r = resultsArray.getJSONObject(i);
            JSONObject item = new JSONObject();
            item.put("title", r.optString("title", ""));
            item.put("url", r.optString("url", ""));
            item.put("snippet", r.optString("content", ""));
            results.put(item);
        }

        JSONObject out = new JSONObject();
        out.put("results", results);
        out.put("count", results.length());
        return out.toString();
    }

    // --- SearXNG ---

    private String searchSearxng(String query, int numResults, WebSearchSettings settings) throws Exception {
        String baseUrl = settings.getSearxngUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("SearXNG URL not configured");
        }

        // Ensure base URL ends without slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String encoded = URLEncoder.encode(query, "UTF-8");
        String urlStr = baseUrl + "/search?q=" + encoded + "&format=json&categories=general";

        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestProperty("Accept", "application/json");

        String response = readResponse(conn);
        int code = conn.getResponseCode();
        conn.disconnect();

        if (code != 200) {
            throw new IOException("HTTP " + code);
        }

        JSONObject obj = new JSONObject(response);
        JSONArray resultsArray = obj.optJSONArray("results");
        if (resultsArray == null) {
            resultsArray = new JSONArray();
        }

        JSONArray results = new JSONArray();
        int limit = Math.min(numResults, resultsArray.length());
        for (int i = 0; i < limit; i++) {
            JSONObject r = resultsArray.getJSONObject(i);
            JSONObject item = new JSONObject();
            item.put("title", r.optString("title", ""));
            item.put("url", r.optString("url", ""));
            item.put("snippet", r.optString("content", ""));
            results.put(item);
        }

        JSONObject out = new JSONObject();
        out.put("results", results);
        out.put("count", results.length());
        return out.toString();
    }

    // --- DuckDuckGo (HTML scraping) ---

    private String searchDuckDuckGo(String query, int numResults) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        String urlStr = "https://lite.duckduckgo.com/lite/?q=" + encoded;

        HttpURLConnection conn = openConnection(urlStr);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        String html = readResponse(conn);
        int code = conn.getResponseCode();
        conn.disconnect();

        if (code != 200) {
            throw new IOException("HTTP " + code);
        }

        // Simple tolerant parser: extract <a> tags with href, then following text
        JSONArray results = new JSONArray();
        int pos = 0;
        int count = 0;

        while (count < numResults && pos < html.length()) {
            // Find next <a rel="nofollow" href="...">
            int linkStart = html.indexOf("<a rel=\"nofollow\" href=\"", pos);
            if (linkStart == -1) break;

            int urlStart = linkStart + 24;
            int urlEnd = html.indexOf("\"", urlStart);
            if (urlEnd == -1) break;
            String url = html.substring(urlStart, urlEnd);

            // Skip if it's a DuckDuckGo internal link
            if (url.contains("duckduckgo.com")) {
                pos = urlEnd + 1;
                continue;
            }

            // Find title (text between > and </a>)
            int titleStart = html.indexOf(">", urlEnd) + 1;
            int titleEnd = html.indexOf("</a>", titleStart);
            if (titleEnd == -1) break;
            String title = html.substring(titleStart, titleEnd).trim();

            // Find snippet (text in next <td> or <div>)
            int snippetStart = html.indexOf("<td", titleEnd);
            if (snippetStart == -1) break;
            snippetStart = html.indexOf(">", snippetStart) + 1;
            int snippetEnd = html.indexOf("</td>", snippetStart);
            if (snippetEnd == -1) break;
            String snippet = html.substring(snippetStart, snippetEnd).trim();

            // Clean HTML tags from snippet
            snippet = snippet.replaceAll("<[^>]*>", "");

            if (!title.isEmpty() && !url.isEmpty()) {
                JSONObject item = new JSONObject();
                item.put("title", title);
                item.put("url", url);
                item.put("snippet", snippet);
                results.put(item);
                count++;
            }

            pos = snippetEnd + 5;
        }

        if (count == 0) {
            throw new IOException("DuckDuckGo returned no results (possible anti-bot block)");
        }

        JSONObject out = new JSONObject();
        out.put("results", results);
        out.put("count", results.length());
        return out.toString();
    }

    // --- HTTP helpers ---

    private HttpURLConnection openConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod("GET");
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Failed to read response: " + e.getMessage());
        }
    }
}
