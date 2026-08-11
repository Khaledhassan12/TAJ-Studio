package pro.sketchware.ai.agent.tools;

import android.content.Context;

/**
 * [WHAT] Context for tool execution.
 */
public class ToolCtx {
    public final Context context;
    public final String scId;
    /** [P2-AU] The conversation the agent turn runs in (null outside chat turns). */
    public final String conversationId;
    public final boolean confirmed;

    public ToolCtx(Context context, String scId, boolean confirmed) {
        this(context, scId, null, confirmed);
    }

    public ToolCtx(Context context, String scId, String conversationId, boolean confirmed) {
        this.context = context;
        this.scId = scId;
        this.conversationId = conversationId;
        this.confirmed = confirmed;
    }
    
    public boolean confirmDestructive() {
        return confirmed;
    }
}
