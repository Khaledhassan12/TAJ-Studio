package pro.sketchware.upgrades.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.upgrades.model.DoctorReport;
import pro.sketchware.upgrades.model.Finding;

/**
 * WHAT: UpgradeReportDialog - Displays a detailed health report for a project.
 * (عربي) حوار تقرير الترقية - يعرض تقرير صحة مفصل للمشروع.
 */
public class UpgradeReportDialog {

    public interface OnApplyFixesListener {
        void onApplyFixes(List<Finding> fixableFindings);
    }

    public static void show(Context context, DoctorReport report, OnApplyFixesListener listener) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle("Project Health: " + report.scId);

        if (report.isEmpty()) {
            builder.setMessage("Everything is up to date & clean ✔");
            builder.setPositiveButton("Close", null);
            builder.show();
            return;
        }

        RecyclerView rv = new RecyclerView(context);
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(new ReportAdapter(report.findings));
        
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        rv.setPadding(padding, padding, padding, padding);
        rv.setClipToPadding(false);

        builder.setView(rv);
        builder.setPositiveButton("Close", null);
        
        if (!report.isEmpty()) {
            builder.setNeutralButton("Apply Safe Fixes", (dialog, which) -> {
                // Confirmation Dialog
                new MaterialAlertDialogBuilder(context)
                    .setTitle("Confirm Fixes")
                    .setMessage("This will delete unused files and apply automated fixes. A backup will be created in .upgrade_backup. Proceed?")
                    .setPositiveButton("Yes, Fix All", (dialog1, which1) -> {
                        if (listener != null) listener.onApplyFixes(report.findings);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }
        
        builder.show();
    }

    private static class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {
        private final List<Finding> findings;

        ReportAdapter(List<Finding> findings) {
            this.findings = findings;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_upgrade_report_detail, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Finding f = findings.get(position);
            holder.title.setText("[" + f.category + "] " + f.title);
            holder.desc.setText(f.detail);
            
            if (f.autoFixable) {
                holder.desc.append("\n" + f.fixDescription);
            }

            // Middle-truncated path for readability
            if (!f.paths.isEmpty()) {
                String path = f.paths.get(0);
                if (path.length() > 40) {
                    path = "..." + path.substring(path.length() - 35);
                }
                holder.desc.append("\nPath: " + path);
            }

            if (f.autoFixable) {
                holder.statusIcon.setImageResource(R.drawable.ic_expire_48dp);
                holder.statusIcon.setVisibility(View.VISIBLE);
            } else {
                holder.statusIcon.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return findings.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, desc;
            ImageView icon, statusIcon;

            ViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.item_title);
                desc = v.findViewById(R.id.item_desc);
                icon = v.findViewById(R.id.item_icon);
                statusIcon = v.findViewById(R.id.item_status_icon);
            }
        }
    }
}
