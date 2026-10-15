# TAJ Studio Agent Execution Audit

Scope: `app/src/main/java/pro/sketchware/ai` on `TAJ-STUDIO-AI`.

This is an evidence-based engineering ledger, not a claim that every acceptance test has passed. A tool is considered operational only when the runtime executes it and independently verifies its effects; model text or a successful API response alone is insufficient.

## Baseline architecture observed

- Agent orchestration: `agent/AgentOrchestrator.java`
- Tool contract and permission gate: `agent/Tool.java`
- Registry and advertised tool catalog: `agent/ToolRegistry.java`
- Project path mapping and containment: `agent/ProjectPaths.java`, `agent/PathResolver.java`
- Filesystem executors: `agent/tools/fs/`
- Sketchware-specific editors and resource/block operations: `agent/tools/`
- Provider-neutral tool schemas: `core/ToolSpec.java`, with provider adapters in `core/` and `net/`
- UI integration and visible tool lifecycle: `ui/AssistantFragment.java`, `ui/ToolAdapter.java`

The foundation already has a real registry, provider tool declarations, an orchestrator loop, project-aware paths, file executors, permission modes, and some post-write checks. These are useful foundations, but their existence does not prove every advertised tool is safe or complete.

## Confirmed issues and changes

### Fixed in this audit

1. **Non-deterministic tool catalog.** The registry used `HashMap`, so tool advertisement order was unstable. It now uses `LinkedHashMap` to keep registration order stable.
2. **Silent registry corruption.** Registration previously overwrote duplicate tool names and accepted null/invalid specs. Registration now rejects null tools, null/blank specs, malformed object schemas, and duplicate names rather than silently replacing an executor.
3. **Invalid build tool schema.** `BuildProjectTool` advertised an empty JSON object without the required `type: object` and `properties` fields. Its schema is now valid.
4. **False-positive build reporting.** The build tool previously returned “Build started” even if the Activity or Run button was absent. It now checks the active editor, waits a bounded time for UI dispatch, reports dispatch failures, and explicitly states that compilation/install/runtime success is not verified by this UI hook.

## High-risk gaps requiring deeper implementation and tests

These are not marked complete until implemented and tested on-device:

- **Destructive operations currently fail closed in the orchestrator.** Delete/remove/move/copy calls are blocked because the current Assistant UI does not provide a per-call approval handshake. This protects data but means these tools are not yet fully usable through the agent. Replace the name-based check with explicit risk metadata on each tool and a real asynchronous approval flow; never unblock destructive calls merely to make them appear functional.
- **Permission dialog synchronization.** The current `ToolContext` waits on a latch for UI confirmation. It must be verified against Activity recreation, cancellation, UI-thread invocation, and timeout. A lifecycle-aware asynchronous approval contract is preferable to blocking a worker on a dialog.
- **Path boundary.** `PathResolver` canonicalizes paths and restricts access to the active project's known roots, which is a valuable defense. Add tests for traversal, absolute paths, symlink escapes, nonexistent destination paths, nested project roots, and paths that resolve differently before/after a filesystem change. Ensure all tools use the same resolver and revalidate immediately before mutation.
- **Backup integrity.** Some writers use a fixed `.bak` suffix. That can collide with a user's own backup, and backup/restore failures must not be ignored. Introduce task-scoped snapshots with content hashes and explicit restore outcomes; preserve pre-existing user files.
- **Atomic writes and conflict detection.** Before write, compare the current file fingerprint with the fingerprint read by the task. Write to a sibling temporary file, flush, then replace atomically where supported. On mismatch, return a conflict without overwriting.
- **Copy/move verification.** Verify directory support, destination parent creation, destination collision behavior, source/destination content hashes, and rollback on partial failure. Do not equate a path existence check with successful content transfer.
- **UI-visible design edits.** File writes alone do not prove the Sketchware designer's in-memory model changed. For layout changes, verify parse success, update the designer model through the native Sketchware path, confirm the target widget exists in the live model, and refresh the editor. Report file persistence and designer synchronization as separate outcomes.
- **Build result observability.** The current Run button hook confirms only dispatch. Wire into the actual build lifecycle and report queued/running/succeeded/failed/cancelled with the build log/result. Until then, do not claim a successful build.
- **Tool argument validation.** Validate required fields, types, enums, maximum lengths, and unexpected fields at the execution boundary. Malformed tool-call JSON must be surfaced as an invalid call, not silently normalized to an empty argument object.
- **Orchestrator termination.** Add explicit result states for step-budget exhaustion, timeout, cancellation, provider failure, tool failure, and partial completion. A final model response after the iteration cap must not be mistaken for task completion when it still requests tools.
- **Concurrency and cancellation.** Prevent overlapping runs from resetting a shared cancellation flag, reject or queue duplicate runs explicitly, propagate cancellation into active tools, and prevent late callbacks from a prior run updating a newer task.
- **Result provenance.** Tool results should carry structured status, path, operation ID, verification evidence, and error category. UI success indicators must derive from executor results and verification, not from model narration.
- **Secret hygiene and untrusted content.** Treat project files and tool outputs as untrusted data, redact credentials from diagnostics, cap output sizes, and ensure instructions embedded in project files cannot expand the executor's permissions.
- **Sketchware source of truth.** Determine whether each edit targets canonical project files, generated files, block metadata, or designer state. Prefer native Sketchware editing APIs for layouts/blocks where needed; validate the persisted artifact and the live editor model separately.

## Required acceptance matrix

| Operation | Required evidence before success |
|---|---|
| List/search/read | Resolved path is in scope; results are bounded; read status and truncation are explicit |
| Create/write/edit | Correct active project; permission granted; no concurrent modification; backup/snapshot safe; reread/hash confirms exact intended change |
| Copy/move/rename | Source and destination verified; bytes/tree contents match; source state matches operation semantics; rollback result recorded |
| Delete | Explicit approval; task-scoped recoverable trash; only target removed from original path; no sibling impact |
| XML/layout/widget | XML parses; resource references are valid; persisted XML matches; Sketchware live model contains requested widget; editor visibly refreshes |
| Java/Kotlin | Patch preserves unrelated content; package/path/language match project; syntax/static checks run where available |
| Manifest/resources/libraries | References and dependency compatibility checked; no duplicate declarations; relevant build result captured |
| Build/run | Actual build system reports terminal outcome; logs attached; no inference from button click |
| Undo | Only this task's changes are reverted; later user edits are preserved; restored hashes and paths verified |

## Release gate

Do not declare production readiness until:
1. Every advertised tool has a registered executor and a valid provider schema.
2. Each tool has success, failure, invalid-input, out-of-scope-path, cancellation, and verification tests.
3. Destructive operations use explicit per-call consent and recoverable changes.
4. Layout operations prove both persisted-file and live-designer effects.
5. Build reporting is wired to the actual build lifecycle.
6. Java/Android build and device-level acceptance tests pass, with their actual logs recorded.

## Verification status for this commit

- Registry ordering/duplicate/schema guard: code updated; Android compilation and runtime registry-construction tests still required.
- Build tool schema and honest dispatch result: code updated; Android compilation and on-device UI dispatch test still required.
- Full tool-by-tool acceptance matrix: not yet executed. No claim is made that all tools, visual updates, undo, or build-result reporting are complete.
