package pro.sketchware.ai.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.R;
import pro.sketchware.ai.download.ModelDownloader;
import pro.sketchware.ai.hf.HfClient;
import pro.sketchware.ai.hf.HfFile;
import pro.sketchware.ai.hf.HfModelSummary;

public class HfSearchSheet extends BottomSheetDialogFragment {

    private HfClient client;
    private ModelDownloader downloader;
    private ResultAdapter resultAdapter;
    private FileAdapter fileAdapter;
    private RecyclerView recycler;
    private View loading;
    private String currentRepoId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_hf_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        client = new HfClient(requireContext());
        downloader = new ModelDownloader(requireContext());
        recycler = view.findViewById(R.id.recycler_results);
        loading = view.findViewById(R.id.loading);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        resultAdapter = new ResultAdapter();
        fileAdapter = new FileAdapter();
        recycler.setAdapter(resultAdapter);

        EditText etSearch = view.findViewById(R.id.et_search);
        TextInputLayout tiSearch = view.findViewById(R.id.ti_search);
        tiSearch.setEndIconOnClickListener(v -> search(etSearch.getText().toString()));
    }

    private void search(String q) {
        if (q.isEmpty()) return;
        loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                List<HfModelSummary> results = client.searchModels(q, 20);
                requireActivity().runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    recycler.setAdapter(resultAdapter);
                    resultAdapter.setItems(results);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ViewHolder> {
        private final List<HfModelSummary> items = new ArrayList<>();
        public void setItems(List<HfModelSummary> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_hf_result, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            HfModelSummary item = items.get(p);
            h.id.setText(item.id);
            h.stats.setText(item.downloads + " downloads • " + item.likes + " likes");
            h.itemView.setOnClickListener(v -> loadFiles(item.id));
        }
        @Override public int getItemCount() { return items.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView id, stats;
            ViewHolder(View v) { super(v); id = v.findViewById(R.id.tv_id); stats = v.findViewById(R.id.tv_stats); }
        }
    }

    private void loadFiles(String repoId) {
        currentRepoId = repoId;
        loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                List<HfFile> files = client.listGgufFiles(repoId);
                requireActivity().runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    recycler.setAdapter(fileAdapter);
                    fileAdapter.setItems(files);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private final List<HfFile> items = new ArrayList<>();
        public void setItems(List<HfFile> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_hf_file, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            HfFile item = items.get(p);
            h.path.setText(item.path);
            h.size.setText((item.size / 1024 / 1024) + " MB");
            h.itemView.setOnClickListener(v -> {
                String modelId = currentRepoId.replace("/", "__") + "__" + item.path;
                downloader.download(currentRepoId, item.path, modelId, new ModelDownloader.DownloadListener() {
                    @Override public void onProgress(String id, long b, Long t) { /* Implementation logic */ }
                    @Override public void onStateChange(String id, String s, String e) {
                        if ("DONE".equals(s)) Toast.makeText(getContext(), "Success!", Toast.LENGTH_SHORT).show();
                    }
                });
                dismiss();
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView path, size;
            ViewHolder(View v) { super(v); path = v.findViewById(R.id.tv_path); size = v.findViewById(R.id.tv_size); }
        }
    }
}
