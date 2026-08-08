package pro.sketchware.ai.agent.tools.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import a.a.a.wq;
import pro.sketchware.ai.agent.tools.*;
import pro.sketchware.utility.FileUtil;

public class SearchTool implements Tool {
    @Override
    public ToolSpec spec() {
        return new ToolSpec("searchProject", "Search for text in project files", "{\"query\": \"string\"}");
    }

    @Override
    public ToolResult execute(ToolArgs args, ToolCtx ctx) {
        String query = args.getString("query");
        if (query == null) return ToolResult.error("Query required");
        
        File root = new File(wq.b(ctx.scId));
        List<String> results = new ArrayList<>();
        search(root, query, results);
        
        if (results.isEmpty()) return ToolResult.success("No matches found");
        return ToolResult.success(String.join("\n", results));
    }

    private void search(File dir, String query, List<String> results) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (f.getName().equals(".upgrade_backup")) continue;
                search(f, query, results);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".txt")) {
                    String content = FileUtil.readFile(f.getAbsolutePath());
                    if (content.contains(query)) {
                        results.add(f.getAbsolutePath());
                        if (results.size() > 50) return; // Limit
                    }
                }
            }
        }
    }
}
