package pro.sketchware.marketplace.dialogs;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.databinding.SheetLibraryVersionPickerBinding;
import pro.sketchware.marketplace.models.MarketplaceLibrary;

/**
 * منتقي إصدارات المكتبة - يقوم بجلب البيانات من مستودعات Maven وترتيبها دلالياً.
 * Library version picker - fetches data from Maven repositories and sorts them semantically.
 */
public class LibraryVersionPickerSheet extends BottomSheetDialogFragment {

    public interface OnVersionPickedListener {
        void onVersionPicked(String version);
    }

    private SheetLibraryVersionPickerBinding binding;
    private MarketplaceLibrary library;
    private OnVersionPickedListener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static LibraryVersionPickerSheet newInstance(MarketplaceLibrary library, OnVersionPickedListener listener) {
        LibraryVersionPickerSheet fragment = new LibraryVersionPickerSheet();
        fragment.library = library;
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetLibraryVersionPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fetchVersions();
        binding.btnRetry.setOnClickListener(v -> fetchVersions());
    }

    private void fetchVersions() {
        binding.pbLoading.setVisibility(View.VISIBLE);
        binding.rvVersions.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                List<String> versions = tryFetchFromRepo("https://dl.google.com/dl/android/maven2/");
                if (versions.isEmpty()) {
                    versions = tryFetchFromRepo("https://repo1.maven.org/maven2/");
                }

                if (versions.isEmpty()) {
                    throw new Exception("No versions found");
                }

                List<String> sortedVersions = sortVersions(versions);
                mainHandler.post(() -> showVersions(sortedVersions));
            } catch (Exception e) {
                Log.e("VersionPicker", "Error fetching versions", e);
                mainHandler.post(() -> showError(e.getMessage()));
            }
        });
    }

    private List<String> tryFetchFromRepo(String baseUrl) throws Exception {
        String[] parts = library.getCoordinate().split(":");
        if (parts.length < 2) return Collections.emptyList();
        
        String groupPath = parts[0].replace('.', '/');
        String artifactId = parts[1];
        String urlString = baseUrl + groupPath + "/" + artifactId + "/maven-metadata.xml";
        
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        if (connection.getResponseCode() != 200) {
            return Collections.emptyList();
        }

        try (InputStream in = connection.getInputStream()) {
            return parseVersions(in);
        }
    }

    private List<String> parseVersions(InputStream in) throws Exception {
        List<String> versions = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, null);
        
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "version".equals(parser.getName())) {
                versions.add(parser.nextText());
            }
            eventType = parser.next();
        }
        return versions;
    }

    private List<String> sortVersions(List<String> versions) {
        Collections.sort(versions, (v1, v2) -> {
            int score1 = getVersionScore(v1);
            int score2 = getVersionScore(v2);
            if (score1 != score2) return score2 - score1;
            return compareSemantic(v2, v1);
        });
        return versions;
    }

    private int getVersionScore(String version) {
        String v = version.toLowerCase();
        if (v.contains("snapshot")) return 0;
        if (v.contains("alpha")) return 1;
        if (v.contains("beta")) return 2;
        if (v.contains("rc")) return 3;
        return 4;
    }

    private int compareSemantic(String v1, String v2) {
        String[] parts1 = v1.split("[.\\-]");
        String[] parts2 = v2.split("[.\\-]");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? tryParse(parts1[i]) : 0;
            int p2 = i < parts2.length ? tryParse(parts2[i]) : 0;
            if (p1 != p2) return p1 - p2;
        }
        return 0;
    }

    private int tryParse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private void showVersions(List<String> versions) {
        binding.pbLoading.setVisibility(View.GONE);
        binding.rvVersions.setVisibility(View.VISIBLE);
        binding.rvVersions.setAdapter(new VersionAdapter(versions));
    }

    private void showError(String message) {
        binding.pbLoading.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message);
    }

    private class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.ViewHolder> {
        private final List<String> versions;

        VersionAdapter(List<String> versions) {
            this.versions = versions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_library_version, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String version = versions.get(position);
            holder.tvVersion.setText(version);
            
            int score = getVersionScore(version);
            if (score < 4) {
                holder.tvTag.setVisibility(View.VISIBLE);
                holder.tvTag.setText(getTagText(score));
            } else {
                holder.tvTag.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onVersionPicked(version);
                dismiss();
            });
        }

        private String getTagText(int score) {
            switch (score) {
                case 0: return "SNAPSHOT";
                case 1: return "ALPHA";
                case 2: return "BETA";
                case 3: return "RC";
                default: return "";
            }
        }

        @Override
        public int getItemCount() {
            return versions.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvVersion;
            TextView tvTag;

            ViewHolder(View itemView) {
                super(itemView);
                tvVersion = itemView.findViewById(R.id.tv_version);
                tvTag = itemView.findViewById(R.id.tv_tag);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
        binding = null;
    }
}