package pro.sketchware.upgrades;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.databinding.FragmentUpgradeCenterBinding;

/**
 * Smart Upgrade Center Fragment - Handles UI for project upgrades.
 * It provides a central place to analyze legacy projects and apply modern standards safely.
 * (عربي) واجهة مركز الترقيات الذكي - تدير شاشة ترقية المشاريع.
 * توفر مكاناً مركزياً لتحليل المشاريع القديمة وتطبيق المعايير الحديثة بأمان.
 */
public class UpgradeCenterFragment extends Fragment {

    private FragmentUpgradeCenterBinding binding;
    private ProjectUpgradeScanner scanner;
    private SafeUpgradeApplier applier;
    private ProjectDoctorOrchestrator doctor;
    private SafeFixApplier fixApplier;
    private UpgradableProjectAdapter adapter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUpgradeCenterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        scanner = new ProjectUpgradeScanner(requireContext());
        applier = new SafeUpgradeApplier(requireContext());
        doctor = new ProjectDoctorOrchestrator(requireContext());
        fixApplier = new SafeFixApplier();
        
        adapter = new UpgradableProjectAdapter(report -> {
            MaterialAlertDialogBuilder confirmBuilder = new MaterialAlertDialogBuilder(requireContext());
            confirmBuilder.setTitle(R.string.upgrade_confirm_title);
            
            StringBuilder itemsText = new StringBuilder();
            for (UpgradeItem item : report.items) {
                if (item.status == UpgradeItem.Status.UPGRADABLE) {
                    itemsText.append("• ").append(item.title).append(": ").append(item.description).append("\n");
                }
            }
            confirmBuilder.setMessage(getString(R.string.upgrade_confirm_msg, report.appName, itemsText.toString()));
            confirmBuilder.setPositiveButton("Upgrade", (dialog, which) -> {
                applier.applyUpgrades(report, new SafeUpgradeApplier.UpgradeCallback() {
                    @Override
                    public void onUpgradeStarted() {
                        Toast.makeText(requireContext(), "Upgrading " + report.appName + "...", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onUpgradeFinished(boolean success, String message) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                            if (success) performScan();
                        }
                    }
                });
            });
            confirmBuilder.setNegativeButton("Cancel", null);
            confirmBuilder.show();
        }, this::showReportDialog);
        
        binding.recyclerUpgrades.setAdapter(adapter);
        
        binding.btnRecheck.setOnClickListener(v -> performScan());
        
        performScan();
    }

    private void performScan() {
        if (!isAdded()) return;
        
        scanner.scan(new ProjectUpgradeScanner.ScanCallback() {
            @Override
            public void onScanStarted() {
                binding.btnRecheck.setVisibility(View.INVISIBLE);
                binding.progressRecheck.setVisibility(View.VISIBLE);
            }

            @Override
            public void onScanFinished(List<UpgradeReport> reports) {
                if (!isAdded()) return;
                
                binding.btnRecheck.setVisibility(View.VISIBLE);
                binding.progressRecheck.setVisibility(View.GONE);
                binding.lastChecked.setText(getString(R.string.last_checked, dateFormat.format(new Date())));
                
                // G7: Summary logic
                int scanned = reports.size();
                int upgradable = 0;
                int clean = 0;
                for (UpgradeReport r : reports) {
                    if (r.isUpToDate()) clean++;
                    else upgradable++;
                }
                
                String summary = getString(R.string.scan_summary_text, scanned, upgradable, clean);
                binding.summaryText.setText(summary);
                
                com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), summary, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();

                if (reports.isEmpty()) {
                    binding.emptyState.setVisibility(View.VISIBLE);
                    binding.recyclerUpgrades.setVisibility(View.GONE);
                } else {
                    binding.emptyState.setVisibility(View.GONE);
                    binding.recyclerUpgrades.setVisibility(View.VISIBLE);
                    adapter.setReports(reports);
                }
            }
        });
    }

    /**
     * WHAT: showReportDialog - Displays a comprehensive project doctor report.
     * (عربي) إظهار حوار التقرير - يعرض تقرير "طبيب المشروع" الشامل.
     */
    private void showReportDialog(UpgradeReport report) {
        doctor.runHealthCheck(report.scId, new ProjectDoctorOrchestrator.DoctorCallback() {
            @Override
            public void onScanStarted() {
                Toast.makeText(requireContext(), "Checking project health...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onScanFinished(pro.sketchware.upgrades.model.DoctorReport doctorReport) {
                if (!isAdded()) return;
                pro.sketchware.upgrades.ui.UpgradeReportDialog.show(requireContext(), doctorReport, selectedFindings -> {
                    fixApplier.applyFixes(report.scId, selectedFindings, new SafeFixApplier.FixCallback() {
                        @Override
                        public void onFixStarted() {
                            Toast.makeText(requireContext(), "Applying fixes...", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFixFinished(boolean success, List<String> appliedFixes) {
                            if (isAdded()) {
                                if (success) {
                                    StringBuilder sb = new StringBuilder("Fixed items:\n");
                                    for (String s : appliedFixes) sb.append("✔ ").append(s).append("\n");
                                    
                                    new MaterialAlertDialogBuilder(requireContext())
                                        .setTitle("Fixes Applied")
                                        .setMessage(sb.toString())
                                        .setPositiveButton("OK", null)
                                        .show();
                                    
                                    performScan(); // Refresh lists
                                } else {
                                    Toast.makeText(requireContext(), "Failed to apply fixes", Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    });
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scanner != null) scanner.shutdown();
        if (applier != null) applier.shutdown();
        if (doctor != null) doctor.shutdown();
        if (fixApplier != null) fixApplier.shutdown();
        binding = null;
    }
}
