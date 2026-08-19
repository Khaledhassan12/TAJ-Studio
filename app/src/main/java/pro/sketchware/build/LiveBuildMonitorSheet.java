package pro.sketchware.build;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.databinding.SheetLiveBuildMonitorBinding;
import pro.sketchware.databinding.ItemBuildLogLineBinding;
import pro.sketchware.R;

public class LiveBuildMonitorSheet implements BuildLogHub.BuildListener {

    private final SheetLiveBuildMonitorBinding binding;
    private final Runnable installCallback;
    private final TextView externalProgressText;
    private final BottomSheetBehavior<View> behavior;
    private final LogAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private long startTime = 0;
    private boolean isTimerRunning = false;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                long elapsed = System.currentTimeMillis() - startTime;
                binding.tvTimer.setText(formatDuration(elapsed));
                handler.postDelayed(this, 1000);
            }
        }
    };

    public LiveBuildMonitorSheet(SheetLiveBuildMonitorBinding binding, String projectName, Runnable installCallback, TextView progressText) {
        this.binding = binding;
        this.installCallback = installCallback;
        this.externalProgressText = progressText;
        
        this.behavior = BottomSheetBehavior.from(binding.getRoot());
        this.behavior.setHideable(true);
        this.behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        this.behavior.setHalfExpandedRatio(0.55f);
        
        this.binding.tvProjectName.setText(projectName);
        
        this.adapter = new LogAdapter();
        this.binding.rvLogs.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext()));
        this.binding.rvLogs.setAdapter(adapter);
        
        this.binding.btnTerminalAction.setOnClickListener(v -> {
            if (this.installCallback != null) {
                this.installCallback.run();
            }
        });

        BuildLogHub.getInstance().addListener(this);
    }

    public void show() {
        behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
    }

    public void setProjectName(String name) {
        binding.tvProjectName.setText(name);
    }

    @Override
    public void onBuildEvent(BuildLogHub.Phase phase, String message, List<BuildLogHub.LogEntry> history) {
        adapter.setLogs(history);
        binding.rvLogs.scrollToPosition(adapter.getItemCount() - 1);
        
        binding.chipPhase.setText(phase.name());
        
        if (externalProgressText != null) {
            externalProgressText.setText(message);
        }

        switch (phase) {
            case RUNNING:
                if (!isTimerRunning) {
                    startTimer();
                }
                binding.cardTerminal.setVisibility(View.GONE);
                break;
                
            case SUCCESS:
                stopTimer();
                showTerminalCard(R.drawable.ic_mtrl_check, "Build succeeded", message, true);
                break;
                
            case FAILURE:
                stopTimer();
                showTerminalCard(R.drawable.ic_mtrl_close, "Build failed", message, false);
                break;
                
            case CANCELED:
                stopTimer();
                showTerminalCard(R.drawable.ic_mtrl_close, "Build canceled", message, false);
                break;
                
            case IDLE:
                stopTimer();
                binding.cardTerminal.setVisibility(View.GONE);
                binding.tvTimer.setText("00:00");
                break;
        }
    }

    private void startTimer() {
        startTime = System.currentTimeMillis();
        isTimerRunning = true;
        handler.removeCallbacks(timerRunnable);
        handler.post(timerRunnable);
    }

    private void stopTimer() {
        isTimerRunning = false;
        handler.removeCallbacks(timerRunnable);
    }

    private void showTerminalCard(int iconRes, String title, String sub, boolean showInstall) {
        binding.cardTerminal.setVisibility(View.VISIBLE);
        binding.imgTerminalIcon.setImageResource(iconRes);
        binding.tvTerminalTitle.setText(title);
        binding.tvTerminalSub.setText(sub);
        binding.btnTerminalAction.setVisibility(showInstall ? View.VISIBLE : View.GONE);
        binding.btnTerminalAction.setText("Install");
    }

    private String formatDuration(long ms) {
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {
        private final List<BuildLogHub.LogEntry> logs = new ArrayList<>();
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

        public void setLogs(List<BuildLogHub.LogEntry> newLogs) {
            logs.clear();
            logs.addAll(newLogs);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemBuildLogLineBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BuildLogHub.LogEntry entry = logs.get(position);
            holder.binding.tvText.setText(entry.message);
            holder.binding.tvTimestamp.setText(timeFormat.format(new Date(entry.timestamp)));
            
            // Basic icon logic based on message content
            if (entry.message.toLowerCase().contains("error") || entry.message.toLowerCase().contains("fail")) {
                holder.binding.imgIcon.setImageResource(R.drawable.ic_mtrl_close);
                holder.binding.imgIcon.setVisibility(View.VISIBLE);
            } else if (entry.message.toLowerCase().contains("success") || entry.message.toLowerCase().contains("complete")) {
                holder.binding.imgIcon.setImageResource(R.drawable.ic_mtrl_check);
                holder.binding.imgIcon.setVisibility(View.VISIBLE);
            } else {
                holder.binding.imgIcon.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemBuildLogLineBinding binding;
            ViewHolder(ItemBuildLogLineBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
