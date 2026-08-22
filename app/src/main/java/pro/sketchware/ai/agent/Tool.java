package pro.sketchware.ai.agent;

import org.json.JSONObject;
import org.json.JSONArray;
import java.lang.ref.WeakReference;
import com.besome.sketch.design.DesignActivity;

public interface Tool {

    class ToolSpec {
        public final String name;
        public final String description;
        public final JSONObject parameters;

        public ToolSpec(String name, String description, JSONObject parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
    }

    class ToolResult {
        public final String content;
        public final boolean error;

        public ToolResult(String content, boolean error) {
            this.content = content;
            this.error = error;
        }

        public static ToolResult structured(String tool, String status, String absolutePath, String message, JSONArray candidates, boolean error) {
            JSONObject json = new JSONObject();
            try {
                json.put("tool", tool);
                json.put("status", status);
                if (absolutePath != null) json.put("absolutePath", absolutePath);
                if (message != null) json.put("message", message);
                if (candidates != null) json.put("candidates", candidates);
            } catch (Exception ignored) {}
            return new ToolResult(json.toString(), error);
        }
    }

    class ToolContext {
        public final WeakReference<DesignActivity> activity;
        public final String sc_id;

        public ToolContext(DesignActivity activity, String sc_id) {
            this.activity = new WeakReference<>(activity);
            this.sc_id = sc_id;
        }

        public void runOnUiThread(Runnable r) {
            DesignActivity a = activity.get();
            if (a != null) a.runOnUiThread(r);
        }

        public boolean requestConfirmation(String message) {
            DesignActivity a = activity.get();
            if (a == null) return false;

            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final boolean[] result = new boolean[1];

            a.runOnUiThread(() -> {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                        .setTitle("TAG Assistant")
                        .setMessage(message)
                        .setPositiveButton("Allow", (d, w) -> {
                            result[0] = true;
                            latch.countDown();
                        })
                        .setNegativeButton("Deny", (d, w) -> {
                            result[0] = false;
                            latch.countDown();
                        })
                        .setCancelable(false)
                        .show();
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                return false;
            }
            return result[0];
        }

        public boolean checkPermission(String path, boolean isOverwrite) {
            DesignActivity a = activity.get();
            if (a == null) return false;

            pro.sketchware.ai.config.AIConfigStore store = pro.sketchware.ai.config.AIConfigStore.getInstance(a);
            String mode = store.getAgentPermMode();

            if (mode.isEmpty()) {
                return showPermissionDialog(a, store, path);
            }

            if ("full".equals(mode)) return true;
            if ("consent".equals(mode)) return requestConfirmation("Agent wants to write to " + path);
            if ("strict".equals(mode)) {
                if (!isOverwrite) return true;
                return requestConfirmation("Agent wants to overwrite " + path);
            }

            return false;
        }

        private boolean showPermissionDialog(DesignActivity a, pro.sketchware.ai.config.AIConfigStore store, String path) {
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final boolean[] result = new boolean[1];

            a.runOnUiThread(() -> {
                android.view.View view = a.getLayoutInflater().inflate(pro.sketchware.R.layout.dialog_agent_permission, null);
                android.widget.TextView tvBody = view.findViewById(pro.sketchware.R.id.tv_body);
                tvBody.setText("The assistant wants to write to " + path);

                android.widget.RadioGroup rg = view.findViewById(pro.sketchware.R.id.rg_modes);

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                        .setTitle("TAG Assistant")
                        .setView(view)
                        .setPositiveButton(pro.sketchware.R.string.ai_allow_and_remember, (d, w) -> {
                            int checked = rg.getCheckedRadioButtonId();
                            String chosenMode = "consent";
                            if (checked == pro.sketchware.R.id.rb_full) chosenMode = "full";
                            else if (checked == pro.sketchware.R.id.rb_strict) chosenMode = "strict";

                            store.setAgentPermMode(chosenMode);
                            result[0] = true;
                            latch.countDown();
                        })
                        .setNegativeButton(pro.sketchware.R.string.ai_deny, (d, w) -> {
                            result[0] = false;
                            latch.countDown();
                        })
                        .setCancelable(false)
                        .show();
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                return false;
            }
            return result[0];
        }
    }

    enum Domain {
        JAVA, RES, ASSET, BLOCK, MANIFEST, PROJECT, FS, LIB
    }

    ToolSpec spec();
    Domain domain();
    ToolResult run(JSONObject args, ToolContext ctx) throws Exception;
}
