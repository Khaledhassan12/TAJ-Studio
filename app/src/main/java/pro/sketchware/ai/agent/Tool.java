package pro.sketchware.ai.agent;

import org.json.JSONObject;
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
    }

    ToolSpec spec();
    ToolResult run(JSONObject args, ToolContext ctx) throws Exception;
}
