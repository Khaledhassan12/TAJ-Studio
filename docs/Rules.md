# TAJ EGYPT - DEVELOPMENT RULES (MANDATORY)

These rules are mandatory for every response and every code modification.

=========================================================
SOURCE OF TRUTH
=========================================================

The uploaded project files, architecture documents, implementation plans, walkthroughs and existing source code are the ONLY source of truth.

Never redesign completed systems.

Never replace existing architecture.

Always continue from the current implementation.

=========================================================
PROJECT GOAL
=========================================================

The project is no longer Agora.

It is TAJ EGYPT.

TAJ EGYPT is an autonomous AI Agent Platform.

Every modification must move the project toward a production-ready Agent Platform.

=========================================================
IMPLEMENTATION STYLE
=========================================================

Implement real production code.

Never generate placeholders.

Never generate TODOs.

Never generate fake implementations.

Never generate demo code.

Never generate example architecture.

Only implement real working code.

=========================================================
CONTINUATION
=========================================================

Every new session must automatically inspect the current project.

Detect what has already been implemented.

Detect what is missing.

Continue from the FIRST unfinished task.

Never repeat previous work.

Never restart completed phases.

=========================================================
COMPATIBILITY
=========================================================

Never break compilation.

Every modification must compile.

Maintain backward compatibility.

Keep existing public APIs working whenever possible.

=========================================================
DATABASE
=========================================================

Every schema change must include:

Migration

Version increment

Backward compatibility

Data preservation

Never destroy user data.

=========================================================
ARCHITECTURE
=========================================================

Keep strict modular architecture.

core modules must never depend on app.

Avoid circular dependencies.

Prefer interfaces over concrete implementations.

Dependency Injection must remain clean.

=========================================================
QUALITY
=========================================================

No duplicated code.

No dead code.

No unused classes.

No unused imports.

No unnecessary abstractions.

Follow SOLID.

Follow Clean Architecture.

Follow Kotlin best practices.

=========================================================
PERFORMANCE
=========================================================

Prefer immutable models.

Avoid unnecessary allocations.

Avoid memory leaks.

Avoid blocking the UI thread.

Use coroutines correctly.

Background work must remain asynchronous.

=========================================================
UI
=========================================================

Never redesign completed UI unless required.

Use Material 3.

Drawer is the primary navigation.

No Bottom Navigation.

Maintain responsive layouts.

=========================================================
AGENT PLATFORM
=========================================================

Everything must integrate with:

Agent Engine

Memory Engine

Tool Engine

Plugin Engine

Reasoning Engine

Model Engine

Dashboard

GenerationManager

=========================================================
WHEN TOKEN LIMIT IS REACHED
=========================================================

Immediately stop.

Do NOT summarize.

Do NOT redesign.

Do NOT explain.

The next session must continue from the exact last unfinished implementation step.

=========================================================
OUTPUT STYLE
=========================================================

Do not write long explanations.

Do not repeat completed work.

Only output:

What was implemented

Files modified

Current build status

Remaining unfinished task

=========================================================
SELF CHECK
=========================================================

Before finishing every response verify:

Project still builds.

No compilation errors introduced.

No dependency cycles.

No broken references.

No unfinished implementations.

=========================================================
MISSION
=========================================================

Continue implementing TAJ EGYPT until the platform becomes a complete production-ready autonomous AI Agent operating system.

Never stop unless the current phase is fully implemented.
Read the current project state first.

Detect everything already implemented.

Do not repeat, redesign, or regenerate completed work.

Continue exactly from the first unfinished implementation task.

Follow all rules in RULES.md.

Modify only real project files.

Keep the project compiling after every change.

Stop only when the current phase reaches 100% completion.