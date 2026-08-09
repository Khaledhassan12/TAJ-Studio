package pro.sketchware.ai.skills;

/**
 * [WHAT] Immutable data holder for an AI skill.
 * [WHY] Defines ready-made workflows for the user to trigger (R6).
 * [HOW] Includes prompt bias suffixes to guide the agent.
 */
public class Skill {
    public final String id;
    public final String title;
    public final String subtitle;
    public final String iconDrawableName;
    public final String promptSuffix;

    public Skill(String id, String title, String subtitle, String iconDrawableName, String promptSuffix) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.iconDrawableName = iconDrawableName;
        this.promptSuffix = promptSuffix;
    }
}
