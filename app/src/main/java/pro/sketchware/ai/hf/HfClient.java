package pro.sketchware.ai.hf;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pro.sketchware.ai.data.SecureKeyStore;

/**
 * [WHAT] Client for interacting with HuggingFace API.
 * [WHY] Allows searching and downloading GGUF models.
 * [HOW] Uses OkHttp for network calls. Public endpoints by default, optional token from SecureKeyStore.
 */
public class HfClient {

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
        String url = "https://huggingface.co/api/models?search=" + query + "&limit=" + limit + "&filter=gguf";
        Request.Builder requestBuilder = new Request.Builder().url(url);
        addAuthHeader(requestBuilder);

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 429) throw new IOException("Rate limited by HuggingFace");
                throw new IOException("Unexpected code " + response);
            }
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
                        tags
                ));
            }
            return results;
        } catch (Exception e) {
            throw new IOException("Search failed: " + e.getMessage());
        }
    }

    public List<HfFile> listGgufFiles(String repoId) throws IOException {
        String url = "https://huggingface.co/api/models/" + repoId + "/tree/main";
        Request.Builder requestBuilder = new Request.Builder().url(url);
        addAuthHeader(requestBuilder);

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
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
            throw new IOException("Listing files failed: " + e.getMessage());
        }
    }

    public String resolveDownloadUrl(String repoId, String fileName) {
        return "https://huggingface.co/" + repoId + "/resolve/main/" + fileName;
    }

    public void downloadToFile(String url, File dest, ProgressCallback cb, CancelFlag flag) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        addAuthHeader(requestBuilder);

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response body");

            long totalBytes = body.contentLength();
            try (InputStream is = body.byteStream();
                 FileOutputStream fos = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int read;
                long downloaded = 0;
                while ((read = is.read(buffer)) != -1) {
                    if (flag != null && flag.isCancelled) {
                        throw new IOException("Download cancelled");
                    }
                    fos.write(buffer, 0, read);
                    downloaded += read;
                    if (cb != null) cb.onProgress(downloaded, totalBytes);
                }
            }
        }
    }

    private void addAuthHeader(Request.Builder builder) {
        String token = keyStore.getHfToken();
        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
    }
}
