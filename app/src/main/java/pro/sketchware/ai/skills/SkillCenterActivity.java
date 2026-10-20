package pro.sketchware.ai.skills;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;

public final class SkillCenterActivity extends BaseAppCompatActivity {
    private static final int REQUEST_IMPORT_ZIP = 7142;
    private SkillManager manager;
    private TabLayout tabs;
    private LinearLayout myPanel, discoverPanel, controlsPanel, myList, controlsList;
    private TextView myCount, myEmpty, lastSelection, discoverStatus;
    private EditText discoverUrl;
    private MaterialSwitch autoSwitch;
    private View progress;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override public void onCreate(@Nullable Bundle state) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(state);
        setContentView(R.layout.activity_skill_center);
        manager = SkillManager.get(this);
        findViewById(R.id.skill_toolbar).setOnClickListener(v -> finish());
        com.google.android.material.appbar.MaterialToolbar toolbar=findViewById(R.id.skill_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        tabs=findViewById(R.id.skill_tabs);
        myPanel=findViewById(R.id.skill_my_panel);
        discoverPanel=findViewById(R.id.skill_discover_panel);
        controlsPanel=findViewById(R.id.skill_controls_panel);
        myList=findViewById(R.id.skill_my_list);
        controlsList=findViewById(R.id.skill_controls_list);
        myCount=findViewById(R.id.skill_my_count);
        myEmpty=findViewById(R.id.skill_my_empty);
        lastSelection=findViewById(R.id.skill_last_selection);
        discoverStatus=findViewById(R.id.skill_discover_status);
        discoverUrl=findViewById(R.id.skill_discover_url);
        autoSwitch=findViewById(R.id.skill_auto_switch);
        progress=findViewById(R.id.skill_discover_progress);

        tabs.addTab(tabs.newTab().setText("My Skills"));
        tabs.addTab(tabs.newTab().setText("Discover"));
        tabs.addTab(tabs.newTab().setText("Controls"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
        });
        findViewById(R.id.skill_create_button).setOnClickListener(v -> showEditor(null));
        findViewById(R.id.skill_import_button).setOnClickListener(v -> launchZipPicker());
        findViewById(R.id.skill_discover_button).setOnClickListener(v -> discoverFromGitHub());
        autoSwitch.setChecked(manager.isAutoSelectEnabled());
        autoSwitch.setOnCheckedChangeListener((button, checked) -> {
            manager.setAutoSelectEnabled(checked);
            Snackbar.make(button, checked ? "Automatic skill selection enabled" : "Automatic selection disabled; pinned skills remain eligible", Snackbar.LENGTH_SHORT).show();
        });
        refreshAll();
        showTab(0);
    }

    @Override public void onResume() { super.onResume(); if(manager!=null) refreshAll(); }
    @Override public void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    private void showTab(int index) {
        myPanel.setVisibility(index==0?View.VISIBLE:View.GONE);
        discoverPanel.setVisibility(index==1?View.VISIBLE:View.GONE);
        controlsPanel.setVisibility(index==2?View.VISIBLE:View.GONE);
        View target=index==0?myPanel:index==1?discoverPanel:controlsPanel;
        target.setAlpha(0f);
        target.setTranslationY(8f);
        target.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
        if(index==2) { autoSwitch.setChecked(manager.isAutoSelectEnabled()); lastSelection.setText(manager.getLastSelectionSummary()); }
    }

    private void refreshAll() { refreshMySkills(); refreshControls(); }
    private void refreshSummaryOnly() {
        lastSelection.setText(manager.getLastSelectionSummary());
        myCount.setText(manager.listSkills().size()+" installed skill"+(manager.listSkills().size()==1?"":"s")+" · stored locally");
    }
    private void refreshMySkills() {
        List<SkillManager.Skill> skills=manager.listSkills();
        myList.removeAllViews();
        myCount.setText(skills.size()+" installed skill"+(skills.size()==1?"":"s")+" · stored locally");
        myEmpty.setVisibility(skills.isEmpty()?View.VISIBLE:View.GONE);
        for(SkillManager.Skill skill:skills) myList.addView(skillCard(skill,false));
    }
    private void refreshControls() {
        controlsList.removeAllViews();
        for(SkillManager.Skill skill:manager.listSkills()) controlsList.addView(skillCard(skill,true));
        lastSelection.setText(manager.getLastSelectionSummary());
    }

    private MaterialCardView skillCard(SkillManager.Skill skill, boolean controlsMode) {
        float d=getResources().getDisplayMetrics().density;
        MaterialCardView card=new MaterialCardView(this);
        card.setRadius(22*d);
        card.setCardElevation(2f*d);
        card.setStrokeWidth(Math.max(1,(int)d));
        card.setStrokeColor(com.google.android.material.color.MaterialColors.getColor(card,com.google.android.material.R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(card,com.google.android.material.R.attr.colorSurfaceContainerLow));
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(15*d); body.setPadding(pad,pad,pad,pad);
        TextView name=new TextView(this); name.setText(skill.name); name.setTextSize(17); name.setTypeface(null,android.graphics.Typeface.BOLD);
        name.setTextColor(com.google.android.material.color.MaterialColors.getColor(card,com.google.android.material.R.attr.colorOnSurface));
        body.addView(name);
        TextView detail=new TextView(this);
        String desc=skill.description==null||skill.description.isEmpty()?"No description supplied":skill.description;
        detail.setText(desc+"\nSource: "+skill.source+"\nFormat: SKILL.md · "+(skill.valid?"compatible structure":"invalid"));
        detail.setTextSize(12);
        detail.setTextColor(com.google.android.material.color.MaterialColors.getColor(card,com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,-2); dp.topMargin=(int)(5*d); body.addView(detail,dp);
        MaterialSwitch enabled=new MaterialSwitch(this); enabled.setText("Skill enabled"); enabled.setChecked(skill.enabled);
        enabled.setOnCheckedChangeListener((b,checked)->{
            manager.setEnabled(skill.id,checked);
            // Avoid destroying/recreating all cards while the originating switch is handling
            // its checked event; that produced rows appearing to swap or resurrect.
            refreshSummaryOnly();
        });
        body.addView(enabled);
        MaterialSwitch pinned=new MaterialSwitch(this); pinned.setText("Always include for agent tasks"); pinned.setChecked(skill.pinned);
        pinned.setOnCheckedChangeListener((b,checked)->{
            manager.setPinned(skill.id,checked);
            refreshSummaryOnly();
        });
        body.addView(pinned);
        if(!controlsMode) {
            LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            MaterialButton edit=new MaterialButton(this); edit.setText("Edit");
            edit.setOnClickListener(v->showEditor(skill));
            MaterialButton remove=new MaterialButton(this); remove.setText("Remove"); remove.setIconResource(R.drawable.ic_mtrl_delete);
            remove.setOnClickListener(v->new MaterialAlertDialogBuilder(this).setTitle("Remove skill?")
                    .setMessage("Remove "+skill.name+" from this device? Its local skill files will be deleted.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Remove",(dialog,which)->{
                        if(manager.deleteSkill(skill.id)) { refreshAll(); Snackbar.make(myPanel,"Skill removed",Snackbar.LENGTH_SHORT).show(); }
                        else Snackbar.make(myPanel,"Could not remove skill files",Snackbar.LENGTH_LONG).show();
                    }).show());
            actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
            actions.addView(remove,new LinearLayout.LayoutParams(0,-2,1));
            body.addView(actions);
        }
        card.addView(body);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.bottomMargin=(int)(10*d);
        card.setLayoutParams(cp);
        card.setAlpha(0f); card.animate().alpha(1f).setDuration(160).start();
        return card;
    }

    private void showEditor(@Nullable SkillManager.Skill skill) {
        LinearLayout fields=new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(8*getResources().getDisplayMetrics().density); fields.setPadding(pad,pad,pad,pad);
        EditText name=field("Skill name",skill==null?"":skill.name,false);
        EditText description=field("Short description (used for auto-selection)",skill==null?"":skill.description,false);
        EditText instructions=field("Instructions (Markdown)",skill==null?"# Goal\nDescribe when this skill applies, the workflow to follow, project conventions, and how to verify results.\n\n## Workflow\n1. Inspect before changing.\n2. Use only available registered tools.\n3. Verify actual results and report limitations.":readSkillText(skill),true);
        fields.addView(name); fields.addView(description); fields.addView(instructions);
        ScrollView scroll=new ScrollView(this); scroll.addView(fields);
        new MaterialAlertDialogBuilder(this).setTitle(skill==null?"Create Skill":"Edit Skill")
                .setMessage("Skills are guidance only. They cannot grant tool permissions or execute bundled scripts.")
                .setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save", (dialog,which)->{
                    String n=name.getText().toString().trim(), d=description.getText().toString().trim(), body=instructions.getText().toString().trim();
                    io.execute(()->{
                        try {
                            if(skill==null) manager.createSkill(n,d,body); else manager.updateSkill(skill.id,n,d,body);
                            runOnUiThread(()->{refreshAll();Snackbar.make(myPanel,"Skill saved locally",Snackbar.LENGTH_SHORT).show();});
                        } catch(Exception e) { runOnUiThread(()->Snackbar.make(myPanel,"Save failed: "+safeMessage(e),Snackbar.LENGTH_LONG).show()); }
                    });
                }).show();
    }
    private String readSkillText(SkillManager.Skill skill) {
        if(skill==null)return "";
        try { java.io.File file=new java.io.File(skill.path,"SKILL.md"); byte[] b=java.nio.file.Files.readAllBytes(file.toPath()); String text=new String(b,java.nio.charset.StandardCharsets.UTF_8); int end=text.indexOf("\n---",3); return end>0?text.substring(end+4).trim():text; }
        catch(Exception e) { return ""; }
    }
    private EditText field(String hint,String value,boolean multiline) {
        EditText edit=new EditText(this); edit.setHint(hint); edit.setText(value); edit.setTextSize(14);
        edit.setInputType(multiline?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setMinLines(multiline?7:1); edit.setMaxLines(multiline?16:3); edit.setGravity(multiline?android.view.Gravity.TOP:android.view.Gravity.CENTER_VERTICAL);
        edit.setPadding(14,12,14,12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=12; edit.setLayoutParams(p); return edit;
    }

    private void launchZipPicker() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip"); intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(intent,REQUEST_IMPORT_ZIP);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,@Nullable Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_IMPORT_ZIP||resultCode!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        io.execute(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                SkillManager.Skill s=manager.importZip(in,"Imported ZIP · "+uri.getLastPathSegment());
                runOnUiThread(()->{refreshAll();Snackbar.make(myPanel,"Imported "+s.name,Snackbar.LENGTH_LONG).show();tabs.selectTab(tabs.getTabAt(0));});
            } catch(Exception e) { runOnUiThread(()->Snackbar.make(myPanel,"Import failed: "+safeMessage(e),Snackbar.LENGTH_LONG).show()); }
        });
    }

    private void discoverFromGitHub() {
        String raw=discoverUrl.getText()==null?"":discoverUrl.getText().toString().trim();
        final String repo=parseGitHubRepo(raw);
        if(repo==null) { discoverStatus.setText("Compatibility: blocked\nEnter a valid https://github.com/owner/repository URL. Other hosts are not accepted."); return; }
        findViewById(R.id.skill_discover_button).setEnabled(false); progress.setVisibility(View.VISIBLE);
        discoverStatus.setText("Source: "+repo+"\nCompatibility: checking repository archive…\nSafety: bundled scripts will not be executed.");
        io.execute(()->{
            HttpURLConnection connection=null;
            try {
                URL url=new URL("https://api.github.com/repos/"+repo+"/zipball");
                connection=(HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(12000); connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept","application/vnd.github+json");
                connection.setRequestProperty("User-Agent","TAJ-Studio-Skills");
                connection.setInstanceFollowRedirects(true);
                int status=connection.getResponseCode();
                if(status<200||status>=300) throw new java.io.IOException("GitHub returned HTTP "+status+". Check repository visibility and URL.");
                try(InputStream in=new BufferedInputStream(connection.getInputStream())) {
                    SkillManager.Skill skill=manager.importZip(in,"GitHub · "+repo);
                    runOnUiThread(()->{
                        discoverStatus.setText("Source: https://github.com/"+repo+"\nCompatibility: SKILL.md found and imported.\nInstalled: "+skill.name+"\nSecurity: scripts were not executed; tools still require runtime permissions.");
                        refreshAll(); Snackbar.make(discoverPanel,"Installed "+skill.name,myPanel.getVisibility()==View.VISIBLE?Snackbar.LENGTH_SHORT:Snackbar.LENGTH_LONG).show();
                        tabs.selectTab(tabs.getTabAt(0));
                    });
                }
            } catch(Exception e) {
                runOnUiThread(()->discoverStatus.setText("Source: "+repo+"\nCompatibility: not installed\nReason: "+safeMessage(e)+"\nCheck that the repository contains a SKILL.md within its first five directory levels."));
            } finally {
                if(connection!=null) connection.disconnect();
                runOnUiThread(()->{progress.setVisibility(View.GONE);findViewById(R.id.skill_discover_button).setEnabled(true);});
            }
        });
    }
    private String parseGitHubRepo(String input) {
        try {
            String normalized=input.replaceFirst("/+$","");
            if(!normalized.startsWith("https://github.com/"))return null;
            String path=Uri.parse(normalized).getPath();
            if(path==null)return null;
            String[] parts=path.replaceFirst("^/","").split("/");
            if(parts.length<2)return null;
            String owner=parts[0], repo=parts[1].replaceFirst("\\.git$","");
            if(!owner.matches("[A-Za-z0-9-]{1,39}")||!repo.matches("[A-Za-z0-9_.-]{1,100}"))return null;
            return owner+"/"+repo;
        } catch(Exception e) { return null; }
    }
    private String safeMessage(Exception e) {
        String message=e.getMessage(); if(message==null||message.trim().isEmpty())return e.getClass().getSimpleName();
        return message.length()>180?message.substring(0,180)+"…":message;
    }
}
