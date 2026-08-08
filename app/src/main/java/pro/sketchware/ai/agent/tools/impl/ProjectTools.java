package pro.sketchware.ai.agent.tools.impl;

import android.os.Handler;
import android.os.Looper;
import java.io.File;
import pro.sketchware.ai.agent.tools.*;
import pro.sketchware.ai.context.ProjectContextManager;
import a.a.a.ProjectBuilder;
import a.a.a.yq;
import a.a.a.lC;
import a.a.a.wq;
import java.util.HashMap;

public class ProjectTools {

    public static class RunBuildTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("runBuild", "Triggers a real project build", "{}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            try {
                HashMap<String, Object> projectInfo = lC.b(ctx.scId);
                yq q = new yq(ctx.context, wq.d(ctx.scId), projectInfo);
                ProjectBuilder builder = new ProjectBuilder(ctx.context, q);
                builder.buildBuiltInLibraryInformation();
                return ToolResult.success("Build initialized successfully. Logs are being recorded.");
            } catch (Exception e) {
                return ToolResult.error("Build failed to start: " + e.getMessage());
            }
        }
    }

    public static class InspectProjectTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("inspectProject", "Get a real snapshot of project metadata and files", "{}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            return ToolResult.success(ProjectContextManager.snapshot(ctx.scId).toString());
        }
    }

    public static class ReadBuildErrorTool implements Tool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("readBuildError", "Read the latest build error log", "{}");
        }

        @Override
        public ToolResult execute(ToolArgs args, ToolCtx ctx) {
            File errorFile = new File(wq.e(), ctx.scId + File.separator + "compile_error");
            if (!errorFile.exists()) return ToolResult.success("No build errors found.");
            return ToolResult.success(pro.sketchware.utility.FileUtil.readFile(errorFile.getAbsolutePath()));
        }
    }
}
