package pro.sketchware.ai.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * [WHAT] Catalog of built-in AI skills.
 * [WHY] Provides a curated set of expert behaviors for the user.
 * [HOW] Static frozen list of Skill objects.
 */
public class SkillCatalog {

    private static final List<Skill> SKILLS;

    static {
        List<Skill> list = new ArrayList<>();
        list.add(new Skill("code_review", "Code Review", 
            "Inspect correctness, lifecycle, threading, memory, security", 
            "ic_mtrl_article", 
            "Perform a thorough Android code review per your Engineering Priority §5. Focus on the 10 inspection dimensions."));
        
        list.add(new Skill("find_bugs", "Find Bugs", 
            "Locate the root cause of crashes, ANRs, or wrong behavior", 
            "ic_mtrl_bug_report", 
            "Debug systematically per §16: Observe → Localize → Root cause → Fix → Verify. Ask for logs if missing."));
        
        list.add(new Skill("optimize", "Optimize Performance", 
            "Measure and fix real bottlenecks, not imagined ones", 
            "ic_mtrl_tune", 
            "Follow §20: inspect the 12 performance dimensions; distinguish known vs potential bottleneck; measure before optimizing."));
        
        list.add(new Skill("translate_ui", "Translate UI", 
            "Add/fix Arabic/English strings without breaking layouts", 
            "ic_mtrl_type", 
            "Handle both languages, preserve IDs, check RTL, verify density independence, do not hardcode strings."));
        
        list.add(new Skill("gen_tests", "Generate Tests", 
            "Meaningful unit + instrumentation tests for risky behaviors", 
            "ic_mtrl_check", 
            "Per §38: meaningful over fake; target highest-risk behaviors; lifecycle + integration where useful."));
        
        list.add(new Skill("refactor", "Safe Refactor", 
            "Restructure without changing behavior", 
            "ic_mtrl_sync", 
            "Per §29: understand → preserve behavior → change structure → verify. Do not mix with feature work."));
        
        SKILLS = Collections.unmodifiableList(list);
    }

    public static List<Skill> list() {
        return SKILLS;
    }
}
