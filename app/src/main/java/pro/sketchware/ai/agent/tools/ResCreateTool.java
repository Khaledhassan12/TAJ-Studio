package pro.sketchware.ai.agent.tools;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import pro.sketchware.ai.agent.Tool;
import pro.sketchware.ai.agent.PathResolver;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.ai.live.LiveRegistry;

public final class ResCreateTool implements Tool {

    @Override
    public ToolSpec spec() {
        try {
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            props.put("folder", new JSONObject().put("type", "string"));
            props.put("name", new JSONObject().put("type", "string"));
            props.put("content", new JSONObject().put("type", "string"));
            props.put("sc_id", new JSONObject().put("type", "string"));
            params.put("properties", props);
            params.put("required", new JSONArray().put("folder").put("name").put("content"));
            return new ToolSpec("res_create", "Creates a resource file with validation.", params);
        } catch (Exception e) { return null; }
    }

    @Override
    public Domain domain() {
        return Domain.RES;
    }

    @Override
    public ToolResult run(JSONObject args, ToolContext ctx) throws Exception {
        String folder = args.getString("folder");
        String name = args.getString("name");
        String content = args.getString("content");
        String sc_id = ctx.sc_id;

        File f = PathResolver.resolve(sc_id, "res/" + folder + "/" + name);
        String path = f.getAbsolutePath();

        if (!ctx.checkPermission(path, false)) return new ToolResult("Denied.", true);

        if (name.endsWith(".xml") && !DomainToolHelper.verifyXml(content)) {
            return ToolResult.structured("res_create", "INVALID_XML", path,
                    "Malformed XML content; no resource file was created or changed.", null, true);
        }

        boolean created = !f.exists();
        if (!created) {
            // A previous attempt may have written the XML but failed before the View tab
            // refreshed. Permit an idempotent layout reconciliation, never an overwrite.
            if (!"layout".equals(folder) || !f.isFile()) {
                return ToolResult.structured("res_create", "RESOURCE_EXISTS", path,
                        "Resource already exists; it was not overwritten.", null, true);
            }
            String existing;
            try {
                existing = DomainToolHelper.readUtf8(f);
            } catch (Exception readError) {
                return ToolResult.structured("res_create", "READ_FAILED", path,
                        "The existing layout could not be read safely; no change was made.", null, true);
            }
            if (!content.equals(existing)) {
                return ToolResult.structured("res_create", "RESOURCE_EXISTS", path,
                        "A layout already exists with different contents. It was not overwritten. Use res_edit for an approved change; this call can only reconcile an identical existing layout.",
                        null, true);
            }
        }

        try {
            if (created) DomainToolHelper.atomicWrite(f, content, null);
            String persisted = DomainToolHelper.readUtf8(f);
            if (!f.isFile() || !content.equals(persisted)) {
                return ToolResult.structured("res_create", "VERIFICATION_FAILED", path,
                        "The resource did not match the requested content after read-back.", null, true);
            }

            if ("layout".equals(folder)) {
                try {
                    DomainToolHelper.syncLayoutToDesigner(sc_id, name, persisted);
                } catch (Exception syncError) {
                    String detail = syncError.getMessage();
                    if (detail == null || detail.trim().isEmpty()) detail = syncError.getClass().getSimpleName();
                    if (detail.length() > 220) detail = detail.substring(0, 220) + "…";
                    // If model synchronization stopped part-way, force the editor's normal
                    // project reload path as a recovery attempt; still report failure.
                    try {
                        // A full project reload can recover when an incremental ViewBean
                        // update failed after the disk write. Accept it only if read-back
                        // and the reloaded live model agree.
                        DomainToolHelper.refreshEditor(ctx, null);
                        if (DomainToolHelper.verifyLiveLayout(sc_id, name, persisted)) {
                            LiveRegistry.notifyChanged(folder, path);
                            return ToolResult.structured("res_create", "SUCCESS", path,
                                    "Exact resource read-back verified; a full project reload restored and verified the live layout model after incremental synchronization failed.",
                                    null, false);
                        }
                        detail += "; full reload completed but the live model still differs";
                    } catch (Exception recoveryError) {
                        String recoveryDetail = recoveryError.getMessage();
                        if (recoveryDetail == null || recoveryDetail.trim().isEmpty()) {
                            recoveryDetail = recoveryError.getClass().getSimpleName();
                        }
                        if (recoveryDetail.length() > 180) recoveryDetail = recoveryDetail.substring(0, 180) + "…";
                        detail += "; full-reload recovery failed: " + recoveryDetail;
                    }
                    return ToolResult.structured("res_create", "DESIGNER_SYNC_FAILED", path,
                            "The XML is present and read-back verified, but neither incremental synchronization nor full-reload verification established the live designer state. The file was preserved; inspect the project before retrying. " + detail,
                            null, true);
                }

                // Refresh BEFORE the final model assertion. Previously a false assertion
                // returned early and left the View tab bound to stale adapter data.
                try {
                    DomainToolHelper.refreshEditor(ctx, name);
                } catch (Exception refreshError) {
                    String detail = refreshError.getMessage();
                    if (detail == null || detail.trim().isEmpty()) detail = refreshError.getClass().getSimpleName();
                    if (detail.length() > 220) detail = detail.substring(0, 220) + "…";
                    return ToolResult.structured("res_create", "DESIGNER_SYNC_FAILED", path,
                            "The XML is present and the model update was attempted, but the active View editor refresh failed. The file was preserved; visual synchronization is not verified. " + detail,
                            null, true);
                }

                boolean verified = DomainToolHelper.verifyLiveLayout(sc_id, name, persisted);
                if (!verified) {
                    // If a cached editor/model remained stale, retry through Sketchware's
                    // normal project reload path, then independently verify the result.
                    DomainToolHelper.refreshEditor(ctx, null);
                    verified = DomainToolHelper.verifyLiveLayout(sc_id, name, persisted);
                }
                if (!verified) {
                    return ToolResult.structured("res_create", "DESIGNER_SYNC_FAILED", path,
                            "The XML is present, but the live Sketchware model still differs after both the targeted View refresh and a full project reload. The file was preserved; visual synchronization is not verified.",
                            null, true);
                }
            }

            LiveRegistry.notifyChanged(folder, path);
            return ToolResult.structured("res_create", "SUCCESS", path,
                    created
                            ? "Exact resource read-back verified" + ("layout".equals(folder) ? "; designer model verified and View editor refreshed." : ".")
                            : "Existing layout content matched exactly; designer model was reconciled and View editor refreshed.",
                    null, false);
        } catch (Exception e) {
            String detail = e.getMessage() == null ? "Resource creation failed." : e.getMessage();
            if (detail.length() > 240) detail = detail.substring(0, 240) + "…";
            return ToolResult.structured("res_create", "ERROR", path,
                    detail + " Inspect the target before retrying because a partial create may have occurred.", null, true);
        }
    }
}
