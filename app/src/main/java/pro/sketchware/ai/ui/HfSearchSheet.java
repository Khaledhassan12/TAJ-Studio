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
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.R;
import pro.sketchware.ai.bus.AiEventHub;
import pro.sketchware.ai.hf.HfModelSummary;
import pro.sketchware.ai.models.ModelManager;

public class HfSearchSheet extends BottomSheetDialogFragment implements AiEventHub.Listener {

    private EditText edSearch;
    private View loading, tvEmpty;
    private ResultAdapter adapter;
    private ModelManager manager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_hf_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        manager = ModelManager.get(requireContext());
        edSearch = view.findViewById(R.id.ed_search);
        loading = view.findViewById(R.id.loading);
        tvEmpty = view.findViewById(R.id.tv_empty);
        RecyclerView recycler = view.findViewById(R.id.recycler_results);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ResultAdapter();
        recycler.setAdapter(adapter);

        view.findViewById(R.id.ti_search).setEndIconOnClickListener(v -> {
            String q = edSearch.getText().toString();
            if (!q.isEmpty()) manager.search(q);
        });

        AiEventHub.get().addListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        AiEventHub.get().removeListener(this);
    }

    @Override
    public void onAiEvent(AiEventHub.Entry entry) {
        if (entry.event == AiEventHub.Event.SEARCHING) {
            loading.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            adapter.setResults(new ArrayList<>());
        } else if (entry.event == AiEventHub.Event.SEARCH_DONE) {
            loading.setVisibility(View.GONE);
            List<HfModelSummary> results = (List<HfModelSummary>) entry.payload;
            adapter.setResults(results);
            tvEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        } else if (entry.event == AiEventHub.Event.ERROR) {
            loading.setVisibility(View.GONE);
            Toast.makeText(requireContext(), (String) entry.payload, Toast.LENGTH_SHORT).show();
        }
    }

    private class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ViewHolder> {
        private final List<HfModelSummary> results = new ArrayList<>();

        public void setResults(List<HfModelSummary> newResults) {
            results.clear();
            results.addAll(newResults);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HfModelSummary s = results.get(position);
            holder.t1.setText(s.id);
            holder.t2.setText(s.downloads + " downloads • " + s.likes + " likes");
            holder.itemView.setOnClickListener(v -> {
                // For P1, we just trigger a hardcoded "resolve and download" the first GGUF found
                // In a real P1, we should show another list of files, but here we simplify or start download directly
                manager.download(s.id, "model.gguf"); // Placeholder fileName, real one needs listGgufFiles
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            TextView t1, t2;
            public ViewHolder(View v) {
                super(v);
                t1 = v.findViewById(android.R.id.text1);
                t2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}
