package pro.sketchware.ai.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.ai.agent.tools.ToolRegistry;
import pro.sketchware.ai.mcp.McpServerConfig;
import pro.sketchware.ai.mcp.McpStore;
import pro.sketchware.ai.ui.AiHeaderInsets;

/**
 * [WHAT] MCP servers list screen (P2-MCP, D16).
 * [WHY] Real management surface for MCP endpoints: enable/disable, edit, delete —
 * every mutation goes through McpStore (single writer, §4) and re-syncs the
 * agent's tool registry, so conversations reflect changes immediately (§2/§11).
 * [HOW] TitleGeneration design pattern; rows inflated from item_mcp_server_row;
 * loading progress while tools/list refresh runs in the background (§16).
 */
public class McpServersActivity extends BaseAppCompatActivity {

    private McpStore store;
    private View cardEmpty;
    private LinearLayout rowsContainer;
    private ProgressBar progressTools;

    private final Runnable storeListener = this::render;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mcp);
        AiHeaderInsets.apply(findViewById(R.id.app_bar));

        store = McpStore.get(this);
        initUi();
        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onResume() {
        super.onResume();
        store.addChangeListener(storeListener);
        render();
        refreshTools();
    }

    @Override
    public void onPause() {
        store.removeChangeListener(storeListener);
        super.onPause();
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        boolean rtl = isRtl();
        ((TextView) findViewById(R.id.tv_display_title)).setText(rtl ? R.string.mcp_title_ar : R.string.mcp_title);
        ((TextView) findViewById(R.id.tv_section_servers)).setText(rtl ? R.string.mcp_section_servers_ar : R.string.mcp_section_servers);
        ((TextView) findViewById(R.id.tv_empty_title)).setText(rtl ? R.string.mcp_empty_title_ar : R.string.mcp_empty_title);
        ((TextView) findViewById(R.id.tv_empty_sub)).setText(rtl ? R.string.mcp_empty_sub_ar : R.string.mcp_empty_sub);
        ((TextView) findViewById(R.id.tv_add_server)).setText(rtl ? R.string.mcp_btn_add_server_ar : R.string.mcp_btn_add_server);

        cardEmpty = findViewById(R.id.card_empty);
        rowsContainer = findViewById(R.id.rows_container);
        progressTools = findViewById(R.id.progress_tools);

        findViewById(R.id.row_add_server).setOnClickListener(v ->
                startActivity(new Intent(this, McpServerEditorActivity.class)));
    }

    /** [R16] UI strictly reflects the persistent state (SSOT = McpStore). */
    private void render() {
        List<McpServerConfig> servers = store.list();
        cardEmpty.setVisibility(servers.isEmpty() ? View.VISIBLE : View.GONE);
        rowsContainer.removeAllViews();

        for (McpServerConfig server : servers) {
            MaterialCardView row = (MaterialCardView) LayoutInflater.from(this)
                    .inflate(R.layout.item_mcp_server_row, rowsContainer, false);

            ((TextView) row.findViewById(R.id.tv_name)).setText(server.name);
            ((TextView) row.findViewById(R.id.tv_sub)).setText(subtitleFor(server));

            MaterialSwitch switchEnabled = row.findViewById(R.id.switch_enabled);
            switchEnabled.setChecked(server.enabled);
            // Single writer: only McpStore mutates the enabled flag.
            switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
                store.setEnabled(server.id, checked);
                ToolRegistry.syncMcp(this);
            });

            row.setOnClickListener(v -> openEditor(server.id));
            row.findViewById(R.id.btn_more).setOnClickListener(v -> showRowMenu(v, server));

            rowsContainer.addView(row);
        }
    }

    /** Mockup subtitle: "<transport> · <host>". */
    private String subtitleFor(McpServerConfig server) {
        String transport = McpServerConfig.TRANSPORT_SSE.equals(server.transport)
                ? getString(R.string.mcp_transport_sse)
                : getString(R.string.mcp_transport_streamable);
        return transport + " · " + hostOf(server.url);
    }

    private String hostOf(String url) {
        try {
            String host = android.net.Uri.parse(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }

    private void openEditor(String serverId) {
        Intent intent = new Intent(this, McpServerEditorActivity.class);
        intent.putExtra(McpServerEditorActivity.EXTRA_SERVER_ID, serverId);
        startActivity(intent);
    }

    private void showRowMenu(View anchor, McpServerConfig server) {
        boolean rtl = isRtl();
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, rtl ? getString(R.string.mcp_menu_edit_ar) : getString(R.string.mcp_menu_edit));
        menu.getMenu().add(0, 2, 1, rtl ? getString(R.string.mcp_menu_delete_ar) : getString(R.string.mcp_menu_delete));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openEditor(server.id);
            } else {
                confirmDelete(server);
            }
            return true;
        });
        menu.show();
    }

    /** App's one destructive-confirm style before the cascade delete. */
    private void confirmDelete(McpServerConfig server) {
        boolean rtl = isRtl();
        new MaterialAlertDialogBuilder(this)
                .setTitle(rtl ? R.string.mcp_dialog_delete_title_ar : R.string.mcp_dialog_delete_title)
                .setMessage(rtl ? getString(R.string.mcp_dialog_delete_msg_ar) : getString(R.string.mcp_dialog_delete_msg))
                .setPositiveButton(rtl ? R.string.mcp_menu_delete_ar : R.string.mcp_menu_delete, (d, w) -> {
                    store.delete(server.id); // cascade: config + encrypted headers + tools cache
                    ToolRegistry.syncMcp(this);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** §16 loading state while the background tools/list refresh runs. */
    private void refreshTools() {
        progressTools.setVisibility(View.VISIBLE);
        store.refreshToolsAsync(() -> {
            if (isFinishing() || isDestroyed()) return;
            progressTools.setVisibility(View.GONE);
            ToolRegistry.syncMcp(this); // pick up refreshed cache even when nothing changed
        });
    }

    private boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
