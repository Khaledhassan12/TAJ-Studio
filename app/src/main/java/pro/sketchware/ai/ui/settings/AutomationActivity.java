package pro.sketchware.ai.ui.settings;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import pro.sketchware.R;
import pro.sketchware.ai.agent.tools.ToolRegistry;
import pro.sketchware.ai.automation.AutomationSettings;

/**
 * [WHAT] Automation settings screen (P2-AU, D17).
 * [WHY] Two independent gates bound to SSOT (R16, §2):
 * - "Access Tasks and Loops" switch ⇄ kv auto_tasks_loops; every toggle
 *   re-syncs ToolRegistry (rebuilds the 5 task/loop tool specs).
 * - "Exact Execution" switch ⇄ kv auto_exact_alarms with a REAL permission
 *   flow: SDK>=S requires AlarmManager.canScheduleExactAlarms() granted via
 *   Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM; deny => persist false +
 *   honest snackbar (no silent ON). The subtitle is DERIVED from state (R16).
 */
public class AutomationActivity extends BaseAppCompatActivity {

    private AutomationSettings settings;
    private View root;
    private MaterialSwitch switchTasksLoops;
    private MaterialSwitch switchExactAlarms;
    private TextView tvExactSub;

    /** Returns from the system "Alarms & reminders" settings screen. */
    private final ActivityResultLauncher<Intent> exactAlarmPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isFinishing() || isDestroyed()) return;
                boolean granted = canScheduleExactAlarms();
                if (granted) {
                    settings.setExactAlarms(true); // single writer (permission flow)
                } else {
                    settings.setExactAlarms(false); // deny => never silent ON
                    Snackbar.make(root, isRtl()
                                    ? getString(R.string.automation_exact_denied_ar)
                                    : getString(R.string.automation_exact_denied),
                            Snackbar.LENGTH_LONG).show();
                }
                renderExactState();
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_automation);

        settings = AutomationSettings.get(this);
        root = findViewById(R.id.automation_root);

        boolean rtl = isRtl();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tv_display_title)).setText(rtl ? R.string.automation_title_ar : R.string.automation_title);
        ((TextView) findViewById(R.id.tv_section_tools)).setText(rtl ? R.string.automation_section_tools_ar : R.string.automation_section_tools);
        ((TextView) findViewById(R.id.tv_section_scheduling)).setText(rtl ? R.string.automation_section_scheduling_ar : R.string.automation_section_scheduling);
        ((TextView) findViewById(R.id.tv_tools_title)).setText(rtl ? R.string.automation_tools_title_ar : R.string.automation_tools_title);
        ((TextView) findViewById(R.id.tv_tools_sub)).setText(rtl ? R.string.automation_tools_sub_ar : R.string.automation_tools_sub);
        ((TextView) findViewById(R.id.tv_exact_title)).setText(rtl ? R.string.automation_exact_title_ar : R.string.automation_exact_title);

        switchTasksLoops = findViewById(R.id.switch_tasks_loops);
        switchExactAlarms = findViewById(R.id.switch_exact_alarms);
        tvExactSub = findViewById(R.id.tv_exact_sub);

        switchTasksLoops.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return; // programmatic renders only
            settings.setTasksLoopsEnabled(isChecked); // single writer (UI)
            ToolRegistry.syncAutomation(this);        // rebuild specs on toggle
        });

        switchExactAlarms.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return; // programmatic renders only
            if (isChecked) {
                requestExactAlarmAccess();
            } else {
                settings.setExactAlarms(false); // single writer (UI)
                renderExactState();
            }
        });

        renderState();
        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onResume() {
        super.onResume();
        // User may have toggled access in system settings behind our back.
        renderState();
        ToolRegistry.syncAutomation(this);
    }

    /** Renders BOTH switches + dynamic subtitle strictly from persisted state (R16). */
    private void renderState() {
        switchTasksLoops.setChecked(settings.isTasksLoopsEnabled());
        renderExactState();
    }

    /** Dynamic subtitle: derived from auto_exact_alarms at EVERY render (§8/§17). */
    private void renderExactState() {
        boolean exact = settings.isExactAlarms();
        switchExactAlarms.setChecked(exact);
        boolean rtl = isRtl();
        if (exact) {
            tvExactSub.setText(rtl ? R.string.automation_exact_sub_on_ar : R.string.automation_exact_sub_on);
        } else {
            tvExactSub.setText(rtl ? R.string.automation_exact_sub_off_ar : R.string.automation_exact_sub_off);
        }
    }

    /**
     * Enable path (REAL flow): SDK>=S without the system grant opens
     * Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM; the launcher callback
     * persists the honest outcome. Below S exact alarms need no runtime grant.
     */
    private void requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            // Keep the switch honest (OFF) until the system grants access.
            switchExactAlarms.setChecked(false);
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            exactAlarmPermissionLauncher.launch(intent);
        } else {
            settings.setExactAlarms(true); // single writer (UI)
            renderExactState();
        }
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
