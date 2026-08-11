package pro.sketchware.ai.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.sketchware.R;
import pro.sketchware.ai.agent.tools.ToolRegistry;
import pro.sketchware.ai.ui.TajSlider;
import pro.sketchware.ai.ui.TajSwitch;
import pro.sketchware.ai.websearch.WebSearchSettings;

/**
 * [WHAT] Settings screen for Web Search.
 * [WHY] P2-WS: Lets the user enable the web_search agent tool and pick its
 * provider + API keys (Java/XML, §2 real binding).
 * [HOW] All UI strictly derived from WebSearchSettings (SSOT, R16); single writer
 * per field; visible rows exist IFF enabled (§8/§17).
 *
 * [العربية]
 * شاشة إعدادات بحث الويب.
 * تُشتق كل حالات الواجهة بصرامة من WebSearchSettings (المصدر الوحيد، R16)،
 * وتظهر الصفوف الفرعية فقط عند تفعيل الميزة.
 */
public class WebSearchActivity extends BaseAppCompatActivity {

    private static final long DEBOUNCE_MS = 500L;

    private WebSearchSettings settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean applyingState = false;

    private TajSwitch switchEnable;
    private View rowProvider, dividerProvider, dividerKey, rowKey, dividerUrl, rowUrl, tvAdvancedLabel, cardAdvanced;
    private TextView tvProviderSub, tvMaxResultsSub;
    private EditText etApiKey, etUrl;
    private TajSlider sliderMaxResults;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_search);

        settings = WebSearchSettings.get(this);

        initUi();
        applyUiState();
        handleInsetts(findViewById(android.R.id.content));
    }

    @Override
    public void onResume() {
        super.onResume();
        syncTool();
        applyUiState();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void initUi() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_docs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/duckduckgo-help-pages/settings/params/"));
            startActivity(intent);
        });

        switchEnable = findViewById(R.id.switch_enable);
        rowProvider = findViewById(R.id.row_provider);
        dividerProvider = findViewById(R.id.divider_provider);
        dividerKey = findViewById(R.id.divider_key);
        rowKey = findViewById(R.id.row_key);
        dividerUrl = findViewById(R.id.divider_url);
        rowUrl = findViewById(R.id.row_url);
        tvAdvancedLabel = findViewById(R.id.tv_advanced_label);
        cardAdvanced = findViewById(R.id.card_advanced);

        tvProviderSub = findViewById(R.id.tv_provider_sub);
        tvMaxResultsSub = findViewById(R.id.tv_max_results_sub);
        etApiKey = findViewById(R.id.et_api_key);
        etUrl = findViewById(R.id.et_url);

        sliderMaxResults = findViewById(R.id.slider_max_results);
        sliderMaxResults.setRange(1f, 10f);
        sliderMaxResults.setStops(new float[]{1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f});

        switchEnable.setOnCheckedChangeListener(isChecked -> {
            if (applyingState) return;
            settings.setEnabled(isChecked);
            syncTool();
            applyUiState();
        });

        rowProvider.setOnClickListener(v -> showProviderDialog());

        etApiKey.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (applyingState) return;
                scheduleSave(() -> settings.setKey(settings.getProvider(), s.toString()));
            }
        });

        etUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (applyingState) return;
                scheduleSave(() -> settings.setSearxngUrl(s.toString()));
            }
        });

        sliderMaxResults.setOnSliderChangeListener(val -> {
            if (applyingState) return;
            settings.setNumResults((int) val);
            tvMaxResultsSub.setText(String.format(getString(R.string.ws_max_results_sub), (int) val));
        });
    }

    private void applyUiState() {
        applyingState = true;
        try {
            boolean enabled = settings.isEnabled();
            switchEnable.setChecked(enabled);

            int subVisibility = enabled ? View.VISIBLE : View.GONE;
            rowProvider.setVisibility(subVisibility);
            dividerProvider.setVisibility(subVisibility);
            tvAdvancedLabel.setVisibility(subVisibility);
            cardAdvanced.setVisibility(subVisibility);

            String provider = settings.getProvider();
            tvProviderSub.setText(WebSearchSettings.getProviderDisplayName(provider));

            boolean needsKey = WebSearchSettings.providerNeedsKey(provider);
            rowKey.setVisibility(enabled && needsKey ? View.VISIBLE : View.GONE);
            dividerKey.setVisibility(enabled && needsKey ? View.VISIBLE : View.GONE);
            if (enabled && needsKey) {
                etApiKey.setText(settings.getKey(provider));
            }

            boolean isSearxng = WebSearchSettings.PROVIDER_SEARXNG.equals(provider);
            rowUrl.setVisibility(enabled && isSearxng ? View.VISIBLE : View.GONE);
            dividerUrl.setVisibility(enabled && isSearxng ? View.VISIBLE : View.GONE);
            if (enabled && isSearxng) {
                etUrl.setText(settings.getSearxngUrl());
            }

            int num = settings.getNumResults();
            sliderMaxResults.setValue((float) num);
            tvMaxResultsSub.setText(String.format(getString(R.string.ws_max_results_sub), num));

        } finally {
            applyingState = false;
        }
    }

    private void scheduleSave(Runnable r) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(r, DEBOUNCE_MS);
    }

    private void syncTool() {
        ToolRegistry.syncWebSearch(this);
    }

    private void showProviderDialog() {
        final String[] providers = {
                WebSearchSettings.PROVIDER_BRAVE,
                WebSearchSettings.PROVIDER_KAGI,
                WebSearchSettings.PROVIDER_SERPER,
                WebSearchSettings.PROVIDER_TAVILY,
                WebSearchSettings.PROVIDER_SEARXNG,
                WebSearchSettings.PROVIDER_DUCKDUCKGO
        };

        ProviderAdapter adapter = new ProviderAdapter(this, providers, settings.getProvider());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ws_provider_title)
                .setAdapter(adapter, null)
                .setNegativeButton(R.string.ai_btn_cancel, null)
                .create();

        adapter.setDialog(dialog);
        dialog.show();
    }

    private class ProviderAdapter extends BaseAdapter {
        private final Context context;
        private final String[] providers;
        private final String current;
        private final LayoutInflater inflater;
        private androidx.appcompat.app.AlertDialog dialog;

        ProviderAdapter(Context context, String[] providers, String current) {
            this.context = context;
            this.providers = providers;
            this.current = current;
            this.inflater = LayoutInflater.from(context);
        }

        void setDialog(androidx.appcompat.app.AlertDialog dialog) {
            this.dialog = dialog;
        }

        @Override public int getCount() { return providers.length; }
        @Override public Object getItem(int position) { return providers[position]; }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_web_search_provider, parent, false);
            }
            String p = providers[position];
            android.widget.RadioButton radio = convertView.findViewById(R.id.radio);
            TextView title = convertView.findViewById(R.id.title);
            TextView desc = convertView.findViewById(R.id.description);

            boolean isSelected = p.equals(current);
            radio.setChecked(isSelected);
            title.setText(WebSearchSettings.getProviderDisplayName(p));
            title.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            
            int descRes = 0;
            switch (p) {
                case WebSearchSettings.PROVIDER_BRAVE: descRes = R.string.ws_desc_brave; break;
                case WebSearchSettings.PROVIDER_KAGI: descRes = R.string.ws_desc_kagi; break;
                case WebSearchSettings.PROVIDER_SERPER: descRes = R.string.ws_desc_serper; break;
                case WebSearchSettings.PROVIDER_TAVILY: descRes = R.string.ws_desc_tavily; break;
                case WebSearchSettings.PROVIDER_SEARXNG: descRes = R.string.ws_desc_searxng; break;
                case WebSearchSettings.PROVIDER_DUCKDUCKGO: descRes = R.string.ws_desc_duckduckgo; break;
            }
            if (descRes != 0) desc.setText(context.getString(descRes));

            convertView.setOnClickListener(v -> {
                settings.setProvider(p);
                applyUiState();
                if (dialog != null) dialog.dismiss();
            });

            return convertView;
        }
    }
}
