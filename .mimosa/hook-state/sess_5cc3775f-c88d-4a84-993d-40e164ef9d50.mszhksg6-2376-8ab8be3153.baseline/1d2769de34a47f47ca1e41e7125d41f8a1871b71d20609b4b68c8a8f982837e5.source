package pro.sketchware.github;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * [AR] كلاس مساعد لعرض حوار اختيار المستودع؛ يتيح إنشاء واحد جديد أو اختيار ريبو موجود.
 * [EN] Helper class to show a repo picker dialog; allows creating a new one or picking an existing repo.
 */
public class UserRepoPicker {

    public interface OnRepoSelectedListener {
        void onRepoSelected(String repoFullName, boolean createNew);
    }

    public static void show(Context context, String defaultRepoSlug, OnRepoSelectedListener listener) {
        GitHubManager manager = GitHubManager.getInstance(context);
        
        List<JsonObject> allItems = new ArrayList<>();
        // Placeholder for "Create new"
        JsonObject createNew = new JsonObject();
        createNew.addProperty("full_name", "➕ Create new: " + defaultRepoSlug);
        createNew.addProperty("is_create", true);
        allItems.add(createNew);

        RepoAdapter adapter = new RepoAdapter(context, allItems);
        
        new MaterialAlertDialogBuilder(context)
                .setTitle("Select Destination Repository")
                .setAdapter(adapter, (dialog, which) -> {
                    JsonObject item = allItems.get(which);
                    if (item.has("is_create")) {
                        listener.onRepoSelected(null, true);
                    } else {
                        listener.onRepoSelected(item.get("full_name").getAsString(), false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();

        manager.listUserRepos(new GitHubManager.RepoListCallback() {
            @Override
            public void onSuccess(List<JsonObject> repos) {
                allItems.addAll(repos);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
                // If fails, we just have the "Create new" option
            }
        });
    }

    private static class RepoAdapter extends BaseAdapter {
        private final Context context;
        private final List<JsonObject> repos;

        RepoAdapter(Context context, List<JsonObject> repos) {
            this.context = context;
            this.repos = repos;
        }

        @Override public int getCount() { return repos.size(); }
        @Override public Object getItem(int position) { return repos.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            JsonObject repo = repos.get(position);
            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);

            text1.setText(repo.get("full_name").getAsString());
            if (repo.has("is_create")) {
                text2.setText("Create a fresh repository for this project");
            } else {
                String desc = (repo.has("description") && !repo.get("description").isJsonNull()) ? 
                        repo.get("description").getAsString() : "No description provided";
                text2.setText(desc);
            }
            return convertView;
        }
    }
}
