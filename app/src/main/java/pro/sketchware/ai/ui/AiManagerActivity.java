package pro.sketchware.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.ai.ui.models.ModelsActivity;
import pro.sketchware.ai.ui.prompts.SystemPromptsActivity;
import pro.sketchware.ai.ui.providers.ProvidersActivity;
import pro.sketchware.databinding.ActivityAiManagerBinding;

/**
 * [WHAT] Main hub for AI settings.
 * [WHY] Provides categorized access to all AI configurations (Providers, Models, Prompts, etc.).
 * [HOW] Uses a RecyclerView with multiple item types (Category Header, Setting Row).
 *
 * [WHAT] المركز الرئيسي لإعدادات الذكاء الاصطناعي.
 * [WHY] يوفر وصولاً مصنفًا إلى جميع تكوينات الذكاء الاصطناعي (المزودون، النماذج، الموجهات، إلخ).
 * [HOW] يستخدم RecyclerView مع أنواع متعددة من العناصر (عنوان الفئة، صف الإعدادات).
 */
public class AiManagerActivity extends BaseAppCompatActivity {

    private ActivityAiManagerBinding binding;
    private final List<Object> items = new ArrayList<>();
    private SettingsAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityAiManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isRtl() ? R.string.ai_manager_title_ar : R.string.ai_manager_title);
        }

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        handleInsetts(binding.getRoot());

        initList();
    }

    @Override
    public void onResume() {
        super.onResume();
        // P2-IG/WS: re-check conditional tool registration on hub return.
        pro.sketchware.ai.agent.tools.ToolRegistry.syncImageGen(this);
        pro.sketchware.ai.agent.tools.ToolRegistry.syncWebSearch(this);
    }

    private void initList() {
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SettingsAdapter();
        binding.recycler.setAdapter(adapter);
        applySettingsList();
    }

    private void applySettingsList() {
        items.clear();

        // الخدمات (Services)
        items.add(new Category(getString(R.string.ai_cat_services), getString(R.string.ai_cat_services_ar)));
        items.add(new SettingRow(R.drawable.ic_mtrl_link, R.string.ai_row_providers, R.string.ai_row_providers_ar, R.string.ai_row_providers_sub, R.string.ai_row_providers_sub_ar, () -> startActivity(new Intent(this, ProvidersActivity.class))));
        items.add(new SettingRow(R.drawable.ic_mtrl_box, R.string.ai_row_models, R.string.ai_row_models_ar, R.string.ai_row_models_sub, R.string.ai_row_models_sub_ar, () -> startActivity(new Intent(this, ModelsActivity.class))));

        // الردود (Responses)
        items.add(new Category(getString(R.string.ai_cat_responses), getString(R.string.ai_cat_responses_ar)));
        items.add(new SettingRow(R.drawable.ic_mtrl_article, R.string.ai_row_prompts, R.string.ai_row_prompts_ar, R.string.ai_row_prompts_sub, R.string.ai_row_prompts_sub_ar, () -> startActivity(new Intent(this, SystemPromptsActivity.class))));
        items.add(new SettingRow(R.drawable.ic_mtrl_tune, R.string.ai_row_generation, R.string.ai_row_generation_ar, R.string.ai_row_generation_sub, R.string.ai_row_generation_sub_ar, () -> startActivity(new Intent(this, GenerationActivity.class))));
        items.add(new SettingRow(R.drawable.ic_mtrl_label, R.string.ai_row_titles, R.string.ai_row_titles_ar, R.string.ai_row_titles_sub, R.string.ai_row_titles_sub_ar, () -> startActivity(new Intent(this, pro.sketchware.ai.ui.settings.TitleGenerationActivity.class))));

        // متعدد الوسائط (Multimodal)
        items.add(new Category(getString(R.string.ai_cat_multimodal), getString(R.string.ai_cat_multimodal_ar)));
        items.add(new SettingRow(R.drawable.ic_mtrl_image, R.string.ai_row_ocr, R.string.ai_row_ocr_ar, R.string.ai_row_ocr_sub, R.string.ai_row_ocr_sub_ar, () -> startActivity(new Intent(this, pro.sketchware.ai.ui.settings.ImageTranscriptionActivity.class))));
        items.add(new SettingRow(R.drawable.ic_cat_image, R.string.ai_row_img_gen, R.string.ai_row_img_gen_ar, R.string.ai_row_img_gen_sub, R.string.ai_row_img_gen_sub_ar, () -> startActivity(new Intent(this, pro.sketchware.ai.ui.settings.ImageGenerationActivity.class))));

        // الأدوات (Tools)
        items.add(new Category(getString(R.string.ai_cat_tools), getString(R.string.ai_cat_tools_ar)));
        items.add(new SettingRow(R.drawable.ic_mtrl_web, R.string.ai_row_web_search, R.string.ai_row_web_search_ar, R.string.ai_row_web_search_sub, R.string.ai_row_web_search_sub_ar, () -> startActivity(new Intent(this, pro.sketchware.ai.ui.settings.WebSearchActivity.class))));
        items.add(new SettingRow(R.drawable.ic_mtrl_search, R.string.ai_row_chat_search, R.string.ai_row_chat_search_ar, R.string.ai_row_chat_search_sub, R.string.ai_row_chat_search_sub_ar, () -> showComingSoon(getString(R.string.ai_row_chat_search))));
        items.add(new SettingRow(R.drawable.ic_log_terminal, R.string.ai_row_shell, R.string.ai_row_shell_ar, R.string.ai_row_shell_sub, R.string.ai_row_shell_sub_ar, () -> showComingSoon(getString(R.string.ai_row_shell))));
        items.add(new SettingRow(R.drawable.ic_mtrl_puzzle, R.string.ai_row_mcp, R.string.ai_row_mcp_ar, R.string.ai_row_mcp_sub, R.string.ai_row_mcp_sub_ar, () -> showComingSoon(getString(R.string.ai_row_mcp))));
        items.add(new SettingRow(R.drawable.ic_mtrl_sync, R.string.ai_row_automation, R.string.ai_row_automation_ar, R.string.ai_row_automation_sub, R.string.ai_row_automation_sub_ar, () -> showComingSoon(getString(R.string.ai_row_automation))));

        // الشبكة (Network)
        items.add(new Category(getString(R.string.ai_cat_network), getString(R.string.ai_cat_network_ar)));
        items.add(new SettingRow(R.drawable.ic_cat_network, R.string.ai_row_proxy, R.string.ai_row_proxy_ar, R.string.ai_row_proxy_sub, R.string.ai_row_proxy_sub_ar, () -> showComingSoon(getString(R.string.ai_row_proxy))));

        // الذاكرة والبيانات (Memory & Data)
        items.add(new Category(getString(R.string.ai_cat_memory), getString(R.string.ai_cat_memory_ar)));
        items.add(new SettingRow(R.drawable.ic_mtrl_time, R.string.ai_row_memory, R.string.ai_row_memory_ar, R.string.ai_row_memory_sub, R.string.ai_row_memory_sub_ar, () -> showComingSoon(getString(R.string.ai_row_memory))));
        items.add(new SettingRow(R.drawable.ic_mtrl_settings_applications, R.string.ai_row_data_control, R.string.ai_row_data_control_ar, R.string.ai_row_data_control_sub, R.string.ai_row_data_control_sub_ar, () -> showComingSoon(getString(R.string.ai_row_data_control))));

        adapter.notifyDataSetChanged();
    }

    private boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    private void showComingSoon(String feature) {
        String msg = isRtl() ? String.format(getString(R.string.ai_msg_coming_soon_ar), feature) : String.format(getString(R.string.ai_msg_coming_soon), feature);
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
    }

    private static class Category {
        String titleEn, titleAr;
        Category(String titleEn, String titleAr) { this.titleEn = titleEn; this.titleAr = titleAr; }
    }

    private static class SettingRow {
        int icon;
        int titleEn, titleAr, subEn, subAr;
        Runnable onClick;
        SettingRow(int icon, int titleEn, int titleAr, int subEn, int subAr, Runnable onClick) {
            this.icon = icon; this.titleEn = titleEn; this.titleAr = titleAr; this.subEn = subEn; this.subAr = subAr; this.onClick = onClick;
        }
    }

    private class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof Category ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                return new CategoryViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_settings_category, parent, false));
            } else {
                return new SettingViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_settings_row, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = items.get(position);
            if (holder instanceof CategoryViewHolder) {
                Category c = (Category) item;
                ((CategoryViewHolder) holder).title.setText(isRtl() ? c.titleAr : c.titleEn);
            } else {
                SettingRow r = (SettingRow) item;
                SettingViewHolder h = (SettingViewHolder) holder;
                h.icon.setImageResource(r.icon);
                h.title.setText(isRtl() ? r.titleAr : r.titleEn);
                h.subtitle.setText(isRtl() ? r.subAr : r.subEn);
                h.itemView.setOnClickListener(v -> r.onClick.run());
            }
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        CategoryViewHolder(View v) { super(v); title = v.findViewById(R.id.category_title); }
    }

    private static class SettingViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title, subtitle;
        SettingViewHolder(View v) { super(v); icon = v.findViewById(R.id.icon); title = v.findViewById(R.id.title); subtitle = v.findViewById(R.id.subtitle); }
    }
}
