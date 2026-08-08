package pro.sketchware.ai.agent.tools;

import android.content.Context;

/**
 * [WHAT] Context for tool execution.
 */
public class ToolCtx {
    public final Context context;
    public final String scId;
    public final boolean confirmed;
    
    public ToolCtx(Context context, String scId, boolean confirmed) {
        this.context = context;
        this.scId = scId;
        this.confirmed = confirmed;
    }
    
    public boolean confirmDestructive() {
        return confirmed;
    }
}
