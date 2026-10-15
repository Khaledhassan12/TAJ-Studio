package pro.sketchware.ai.agent;

import org.json.JSONObject;
import org.json.JSONArray;
import java.lang.ref.WeakReference;
import com.besome.sketch.design.DesignActivity;

public interface Tool {

    enum RiskLevel { STANDARD, DESTRUCTIVE }

    /** Explicit operation risk; destructive operations require per-call approval. */
    default RiskLevel riskLevel(org.json.JSONObject args) { return RiskLevel.STANDARD; }

    /** Context-aware risk classification for operations such as overwrite-vs-create. */
    default RiskLevel riskLevel(org.json.JSONObject args, ToolContext context) {
        return riskLevel(args);
    }

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
        private final ThreadLocal<Boolean> approvedMutation = new ThreadLocal<>();

        public void beginApprovedMutation() { approvedMutation.set(Boolean.TRUE); }
        public void endApprovedMutation() { approvedMutation.remove(); }

        public ToolContext(DesignActivity activity, String sc_id) {
            this.activity = new WeakReference<>(activity);
            this.sc_id = sc_id;
        }

        public void runOnUiThread(Runnable r) {
            DesignActivity a = activity.get();
            if (a != null) a.runOnUiThread(r);
        }

        private volatile java.util.function.BooleanSupplier cancellationCheck = () -> false;

        public void setCancellationCheck(java.util.function.BooleanSupplier check) {
            cancellationCheck = check == null ? () -> false : check;
        }

        public boolean isCancellationRequested() {
            return isCancelled(cancellationCheck);
        }

        public boolean requestConfirmation(String message) {
            return requestConfirmation(message, cancellationCheck);
        }

        /** Waits off the UI thread, but observes task cancellation and dismisses its own dialog. */
        public boolean requestConfirmation(String message, java.util.function.BooleanSupplier cancelled) {
            DesignActivity a = activity.get();
            if (a == null || isCancelled(cancelled)) return false;

            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(false);
            final java.util.concurrent.atomic.AtomicReference<androidx.appcompat.app.AlertDialog> dialogRef =
                    new java.util.concurrent.atomic.AtomicReference<>();

            a.runOnUiThread(() -> {
                try {
                    if (a.isFinishing() || a.isDestroyed() || isCancelled(cancelled)) {
                        latch.countDown();
                        return;
                    }
                    androidx.appcompat.app.AlertDialog dialog =
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                                    .setTitle("TAG Assistant")
                                    .setMessage(message)
                                    .setPositiveButton("Allow", (d, w) -> {
                                        result.set(true);
                                        latch.countDown();
                                    })
                                    .setNegativeButton("Deny", (d, w) -> {
                                        result.set(false);
                                        latch.countDown();
                                    })
                                    .setOnDismissListener(d -> latch.countDown())
                                    .setCancelable(false)
                                    .create();
                    dialogRef.set(dialog);
                    if (isCancelled(cancelled) || a.isFinishing() || a.isDestroyed()) {
                        dialog.dismiss();
                        latch.countDown();
                    } else {
                        dialog.show();
                    }
                } catch (RuntimeException e) {
                    latch.countDown();
                }
            });

            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(2);
            try {
                while (latch.getCount() > 0) {
                    if (isCancelled(cancelled) || System.nanoTime() >= deadline) {
                        dismissConfirmation(a, dialogRef);
                        return false;
                    }
                    latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                dismissConfirmation(a, dialogRef);
                return false;
            }
            return result.get() && !isCancelled(cancelled);
        }

        private static boolean isCancelled(java.util.function.BooleanSupplier cancelled) {
            try { return cancelled != null && cancelled.getAsBoolean(); }
            catch (RuntimeException ignored) { return true; }
        }

        private static void dismissConfirmation(DesignActivity activity,
                java.util.concurrent.atomic.AtomicReference<androidx.appcompat.app.AlertDialog> dialogRef) {
            activity.runOnUiThread(() -> {
                androidx.appcompat.app.AlertDialog dialog = dialogRef.get();
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
            });
        }

        public boolean checkPermission(String path, boolean isOverwrite) {
            // A scoped per-call approval is granted by AgentOrchestrator only after a real
            // confirmation dialog. It expires when that tool invocation returns.
            if (Boolean.TRUE.equals(approvedMutation.get())) return true;
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
                try {
                    if (a.isFinishing() || a.isDestroyed()) {
                        latch.countDown();
                        return;
                    }
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
                            .setOnDismissListener(d -> latch.countDown())
                            .setCancelable(false)
                            .show();
                } catch (RuntimeException e) {
                    latch.countDown();
                }
            });

            try {
                if (!latch.await(2, java.util.concurrent.TimeUnit.MINUTES)) return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return result[0];
        }
    }

    enum Domain {
        JAVA, RES, ASSET, BLOCK, MANIFEST, PROJECT, FS, LIB
    }

    /** Safe, concise context shown to the user before a destructive operation. */
    default String approvalSummary(JSONObject args, ToolContext ctx) { return ""; }

    ToolSpec spec();
    Domain domain();
    ToolResult run(JSONObject args, ToolContext ctx) throws Exception;
}
