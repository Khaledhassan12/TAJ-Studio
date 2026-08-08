package pro.sketchware.ai.hf;

import android.content.Context;
import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import pro.sketchware.ai.data.SecureKeyStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * [WHAT] Client for HuggingFace API.
 * [WHY] Handles searching, listing, and downloading GGUF models.
 * [HOW] Uses OkHttp with Bearer token from SecureKeyStore. Supports Range resume.
 */
public class HfClient {

    private static final String TAG = "HfClient";
    private final OkHttpClient client;
    private final SecureKeyStore keyStore;

    public HfClient(Context context) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.keyStore = SecureKeyStore.get(context);
    }

    public List<HfModelSummary> searchModels(String query, int limit) throws IOException {
        String url = "https://huggingface.co/api/models?search=" + query + "&limit=" + limit;
        Request request = buildRequest(url).build();

        try (Response response = client.newCall(request).execute()) {
            handleErrorResponses(response);
            String body = response.body().string();
            JSONArray arr = new JSONArray(body);
            List<HfModelSummary> results = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                List<String> tags = new ArrayList<>();
                if (obj.has("tags")) {
                    JSONArray tagsArr = obj.getJSONArray("tags");
                    for (int j = 0; j < tagsArr.length(); j++) tags.add(tagsArr.getString(j));
                }
                results.add(new HfModelSummary(
                        obj.getString("id"),
                        obj.optInt("downloads", 0),
                        obj.optInt("likes", 0),
                        tags,
                        obj.optString("pipeline_tag", null)
                ));
            }
            return results;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Search failed: " + e.getMessage());
        }
    }

    public List<HfFile> listGgufFiles(String repoId) throws IOException {
        String url = "https://huggingface.co/api/models/" + repoId + "/tree/main?recursive=true";
        Request request = buildRequest(url).build();

        try (Response response = client.newCall(request).execute()) {
            handleErrorResponses(response);
            String body = response.body().string();
            JSONArray arr = new JSONArray(body);
            List<HfFile> files = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String path = obj.getString("path");
                if (path.toLowerCase().endsWith(".gguf")) {
                    files.add(new HfFile(path, obj.optLong("size", 0)));
                }
            }
            return files;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Listing files failed: " + e.getMessage());
        }
    }

    public String resolveDownloadUrl(String repoId, String fileName) {
        return "https://huggingface.co/" + repoId + "/resolve/main/" + fileName;
    }

    public void downloadToFile(String url, File destPart, ProgressCallback cb, CancelFlag flag) throws IOException {
        long existingLength = destPart.exists() ? destPart.length() : 0;
        Request.Builder rb = buildRequest(url);
        if (existingLength > 0) {
            rb.addHeader("Range", "bytes=" + existingLength + "-");
        }

        try (Response response = client.newCall(rb.build()).execute()) {
            handleErrorResponses(response);
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty body");

            long totalContent = body.contentLength();
            Long totalSize = (totalContent == -1) ? null : totalContent + existingLength;

            try (InputStream is = body.byteStream();
                 RandomAccessFile raf = new RandomAccessFile(destPart, "rw")) {
                raf.seek(existingLength);
                byte[] buffer = new byte[8192];
                int read;
                long totalRead = existingLength;
                while ((read = is.read(buffer)) != -1) {
                    if (flag != null && flag.isCancelled()) {
                        throw new IOException("Cancelled");
                    }
                    raf.write(buffer, 0, read);
                    totalRead += read;
                    if (cb != null) cb.onProgress(totalRead, totalSize);
                }
            }
        }
    }

    private Request.Builder buildRequest(String url) {
        Request.Builder rb = new Request.Builder().url(url);
        String token = keyStore.getHfToken();
        if (token != null && !token.isEmpty()) {
            rb.addHeader("Authorization", "Bearer " + token);
        }
        return rb;
    }

    private void handleErrorResponses(Response response) throws IOException {
        if (!response.isSuccessful()) {
            if (response.code() == 429) throw new IOException("Rate limited by HuggingFace");
            if (response.code() == 401 || response.code() == 403) throw new IOException("Auth failure");
            throw new IOException("HTTP " + response.code() + ": " + response.message());
        }
    }
}
