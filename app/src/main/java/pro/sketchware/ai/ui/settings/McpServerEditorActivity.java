package pro.sketchware.ai.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.ai.agent.tools.ToolRegistry;
import pro.sketchware.ai.mcp.McpClient;
import pro.sketchware.ai.mcp.McpServerConfig;
import pro.sketchware.ai.mcp.McpStore;
import pro.sketchware.ai.ui.PillTabSwitcher;

/**
 * [WHAT] Add/Edit MCP server editor (P2-MCP, D16).
 * [WHY] Mockup-verbatim surface to configure Streamable HTTP / SSE endpoints.
 * Save performs a REAL initialize handshake (+ tools/list) on a background
 * thread; only on success is anything persisted (§2/§11/§16).
 * [HOW] Single writer = McpStore; header values go ONLY to SecureKeyStore
 * (RISK-4); failures show a typed honest message and persist nothing.
 */
public class McpServerEditorActivity extends BaseAppCompatActivity {

    public static final String EXTRA_SERVER_ID = "mcp_server_id";

    private McpStore store;
    @Nullable
    private McpServerConfig original; // null => add mode

    private TextInputLayout tilName;
    private EditText etName;
    private TextInputLayout tilUrl;
    private EditText etUrl;
    private PillTabSwitcher pillTransport;
    private View cardHeadersEmpty;
    private LinearLayout headersContainer;
    private MaterialButton btnSave;
    private ProgressBar progressConnect;

    private final List<HeaderRow> headerRows = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean connecting = false;

    private static class HeaderRow {
        View root;
        TextInputLayout tilName;
        EditText etName;
        TextInputLayout tilValue;
        EditText etValue;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mcp_server_editor);

        store = McpStore.get(this);
        String serverId = getIntent().getStringExtra(EXTRA_SERVER_ID);
        if (serverId != null) {
            original = store.findById(serverId);
        }

        initUi();
        prefill();
        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void initUi() {
        boolean rtl = isRtl();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TextView title = findViewById(R.id.tv_display_title);
        if (original != null) {
            title.setText(rtl ? R.string.mcp_editor_title_edit_ar : R.string.mcp_editor_title_edit);
        } else {
            title.setText(rtl ? R.string.mcp_editor_title_add_ar : R.string.mcp_editor_title_add);
        }

        ((TextView) findViewById(R.id.tv_section_connection)).setText(rtl ? R.string.mcp_section_connection_ar : R.string.mcp_section_connection);
        ((TextView) findViewById(R.id.tv_section_headers)).setText(rtl ? R.string.mcp_section_headers_ar : R.string.mcp_section_headers);
        ((TextView) findViewById(R.id.tv_headers_empty_title)).setText(rtl ? R.string.mcp_empty_headers_title_ar : R.string.mcp_empty_headers_title);
        ((TextView) findViewById(R.id.tv_headers_empty_sub)).setText(rtl ? R.string.mcp_empty_headers_sub_ar : R.string.mcp_empty_headers_sub);
        ((TextView) findViewById(R.id.tv_add_header)).setText(rtl ? R.string.mcp_btn_add_header_ar : R.string.mcp_btn_add_header);

        tilName = findViewById(R.id.til_name);
        etName = findViewById(R.id.et_name);
        tilUrl = findViewById(R.id.til_url);
        etUrl = findViewById(R.id.et_url);
        tilName.setHint(rtl ? getString(R.string.mcp_hint_name_ar) : getString(R.string.mcp_hint_name));
        tilUrl.setHint(rtl ? getString(R.string.mcp_hint_url_ar) : getString(R.string.mcp_hint_url));

        pillTransport = findViewById(R.id.pill_transport);
        pillTransport.setTabs(getString(R.string.mcp_transport_streamable), getString(R.string.mcp_transport_sse));

        cardHeadersEmpty = findViewById(R.id.card_headers_empty);
        headersContainer = findViewById(R.id.headers_container);
        btnSave = findViewById(R.id.btn_save);
        progressConnect = findViewById(R.id.progress_connect);

        findViewById(R.id.row_add_header).setOnClickListener(v -> addHeaderRow("", ""));
        btnSave.setOnClickListener(v -> attemptSave());

        TextWatcher validityWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilName.setError(null);
                tilUrl.setError(null);
                updateSaveEnabled();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        etName.addTextChangedListener(validityWatcher);
        etUrl.addTextChangedListener(validityWatcher);

        updateSaveEnabled();
        updateHeadersEmptyState();
    }

    private void prefill() {
        if (original == null) return;
        etName.setText(original.name);
        etUrl.setText(original.url);
        pillTransport.setSelectedIndex(McpServerConfig.TRANSPORT_SSE.equals(original.transport) ? 1 : 0);
        for (McpServerConfig.Header header : original.headers) {
            String value = store.getHeaderValue(original.id, header.name);
            addHeaderRow(header.name, value != null ? value : "");
        }
    }

    private void addHeaderRow(String name, String value) {
        boolean rtl = isRtl();
        HeaderRow row = new HeaderRow();
        row.root = LayoutInflater.from(this).inflate(R.layout.item_mcp_header_row, headersContainer, false);
        row.tilName = row.root.findViewById(R.id.til_header_name);
        row.etName = row.root.findViewById(R.id.et_header_name);
        row.tilValue = row.root.findViewById(R.id.til_header_value);
        row.etValue = row.root.findViewById(R.id.et_header_value);

        row.tilName.setHint(rtl ? getString(R.string.mcp_hint_header_name_ar) : getString(R.string.mcp_hint_header_name));
        row.tilValue.setHint(rtl ? getString(R.string.mcp_hint_header_value_ar) : getString(R.string.mcp_hint_header_value));
        row.etName.setText(name);
        row.etValue.setText(value);

        row.root.findViewById(R.id.btn_header_delete).setOnClickListener(v -> {
            headersContainer.removeView(row.root);
            headerRows.remove(row);
            updateHeadersEmptyState();
        });

        headerRows.add(row);
        headersContainer.addView(row.root);
        updateHeadersEmptyState();
    }

    private void updateHeadersEmptyState() {
        cardHeadersEmpty.setVisibility(headerRows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // --- Validation (save enabled iff valid) ---

    private boolean nameValid() {
        return !etName.getText().toString().trim().isEmpty();
    }

    private boolean urlValid(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void updateSaveEnabled() {
        if (connecting) return;
        btnSave.setEnabled(nameValid() && urlValid(etUrl.getText().toString().trim()));
    }

    // --- Save: real handshake first, persist only on success ---

    private void attemptSave() {
        boolean rtl = isRtl();
        String name = etName.getText().toString().trim();
        String url = etUrl.getText().toString().trim();

        if (name.isEmpty()) {
            tilName.setError(rtl ? getString(R.string.mcp_err_name_required_ar) : getString(R.string.mcp_err_name_required));
            return;
        }
        if (!urlValid(url)) {
            tilUrl.setError(rtl ? getString(R.string.mcp_err_invalid_url_ar) : getString(R.string.mcp_err_invalid_url));
            return;
        }

        // Candidate config used for the handshake BEFORE anything is persisted.
        McpServerConfig candidate = new McpServerConfig();
        candidate.id = original != null ? original.id : UUID.randomUUID().toString();
        candidate.name = name;
        candidate.url = url;
        candidate.transport = pillTransport.getSelectedIndex() == 1
                ? McpServerConfig.TRANSPORT_SSE : McpServerConfig.TRANSPORT_STREAMABLE_HTTP;
        candidate.enabled = original == null || original.enabled;

        List<String[]> headerInputs = new ArrayList<>(); // [name, value]
        for (HeaderRow row : headerRows) {
            String headerName = row.etName.getText().toString().trim();
            if (headerName.isEmpty()) continue;
            headerInputs.add(new String[]{headerName, row.etValue.getText().toString()});
            candidate.headers.add(new McpServerConfig.Header(headerName));
        }

        setConnecting(true);
        executor.execute(() -> {
            String failure = null;
            List<McpClient.McpToolInfo> tools = null;
            try {
                McpClient client = new McpClient(this, candidate);
                client.initialize();
                tools = client.listTools();
            } catch (Exception e) {
                failure = e.getMessage() != null ? e.getMessage() : "unknown error";
            }

            final String error = failure;
            final List<McpClient.McpToolInfo> finalTools = tools;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (error != null) {
                    setConnecting(false);
                    showError(error); // honest message, nothing persisted
                } else {
                    persist(candidate, headerInputs, finalTools);
                }
            });
        });
    }

    private void setConnecting(boolean active) {
        connecting = active;
        progressConnect.setVisibility(active ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!active && nameValid() && urlValid(etUrl.getText().toString().trim()));
        btnBackEnabled(!active);
    }

    private void btnBackEnabled(boolean enabled) {
        findViewById(R.id.btn_back).setEnabled(enabled);
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(String.format(Locale.US,
                        getString(R.string.mcp_msg_handshake_failed), message))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Runs only after a successful handshake (SSOT writes via McpStore). */
    private void persist(McpServerConfig candidate, List<String[]> headerInputs, List<McpClient.McpToolInfo> tools) {
        store.upsert(candidate);

        // Header values: SecureKeyStore ONLY (RISK-4). Empty value => remove key.
        Set<String> keptNames = new HashSet<>();
        for (String[] input : headerInputs) {
            keptNames.add(input[0]);
            if (input[1].isEmpty()) {
                store.removeHeaderValue(candidate.id, input[0]);
            } else {
                store.putHeaderValue(candidate.id, input[0], input[1]);
            }
        }
        // Remove encrypted values of headers that were renamed or deleted.
        if (original != null) {
            for (McpServerConfig.Header old : original.headers) {
                if (!keptNames.contains(old.name)) {
                    store.removeHeaderValue(candidate.id, old.name);
                }
            }
        }

        if (tools != null) {
            store.writeToolsCache(candidate.id, tools);
        }
        ToolRegistry.syncMcp(this);
        finish();
    }

    private boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
