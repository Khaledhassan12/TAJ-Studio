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

### Assistant session and UI lifecycle audit

- Session persistence now stores a display title and last-updated timestamp, sorts history by recent activity, and provides queued disk writes so token streaming does not fsync the entire transcript on every token.
- The history panel refreshes when revisited and after request completion. Tool and thinking indicators are terminalized when a request is stopped; orphaned RUNNING tool events are repaired when a saved session is restored.
- Device acceptance tests are still required for process-death persistence, provider completion/cancellation races, UI-thread responsiveness, and actual project-tool latency.

### Fixed in this audit

1. **Non-deterministic tool catalog.** The registry used `HashMap`, so tool advertisement order was unstable. It now uses `LinkedHashMap` to keep registration order stable.
2. **Silent registry corruption.** Registration previously overwrote duplicate tool names and accepted null/invalid specs. Registration now rejects null tools, null/blank specs, malformed object schemas, and duplicate names rather than silently replacing an executor.
3. **Invalid build tool schema.** `BuildProjectTool` advertised an empty JSON object without the required `type: object` and `properties` fields. Its schema is now valid.
4. **False-positive build reporting.** The build tool now validates the active editor and project, checks cancellation and the Run button, binds to a unique build session, and waits for the build system's terminal compile/sign result. It explicitly does not equate APK creation with install or runtime success.


### Additional execution-integrity safeguards in the current candidate update

- Added executor-boundary validation of required fields, JSON types, enums, argument/string sizes, collection sizes and nesting before a tool can run.
- Registry schemas are defensive copies, and undeclared top-level arguments are rejected rather than silently ignored. The same registered schema contract is used for provider declarations and executor validation.
- Bound tool calls to the project ID captured at task start and reject an explicitly mismatched project ID.
- Replaced the blanket name-based destructive-call block with explicit risk metadata and one-call approval for filesystem delete/copy/move, resource delete/move/copy, asset delete, library add/remove, shared block create/edit, and manifest changes.
- Added context-aware write risk classification: creating a new file can proceed under the configured permission policy, while replacing an existing regular file requires a per-call approval even in persistent `full` permission mode.
- Added a project-wide agent run lock, cancellation-aware approval wait, five-minute runtime deadline, ten-round cap, and explicit error if the budget ends with unresolved tool calls.
- Tracked executor failures across the entire task, not only the last tool batch, and appended a final warning if any tool failed at least once so later model narration cannot silently erase evidence of a failed or partially applied operation.
- Added exact UTF-8 read-back and SHA-256 evidence to the updated create/edit/copy/move/delete and metadata writers; the tools now refuse silent overwrite of existing create/copy destinations.
- Bounded file reads and list/search output; incomplete traversal is marked partial rather than complete success.
- XML parsing now advances to END_DOCUMENT, and resource/layout/widget tools check persisted content and live Sketchware model parity before reporting full success.

These are code-level changes only until CI compiles this exact commit and the acceptance tests below pass on Android. They do not constitute proof of device-visible behavior.

## High-risk gaps requiring deeper implementation and tests

These are not marked complete until implemented and tested on-device:

- **Consent-flow lifecycle and usability.** Destructive executors have explicit risk metadata and per-call consent. Approval waits are bounded and now observe task cancellation, dismiss their dialog on cancellation, and fail closed on timeout. The worker still uses a latch; Activity recreation, app backgrounding, rapid task changes, and device behavior require acceptance tests. A lifecycle-aware asynchronous confirmation contract remains preferable to a blocking worker wait.
- **Permission dialog synchronization.** The current `ToolContext` waits on a latch for UI confirmation. It must be verified against Activity recreation, cancellation, UI-thread invocation, and timeout. A lifecycle-aware asynchronous approval contract is preferable to blocking a worker on a dialog.
- **Path boundary.** `PathResolver` canonicalizes paths and restricts access to the active project's known roots, which is a valuable defense. Add tests for traversal, absolute paths, symlink escapes, nonexistent destination paths, nested project roots, and paths that resolve differently before/after a filesystem change. Ensure all tools use the same resolver and revalidate immediately before mutation.
- **Durable undo and snapshot lifecycle.** Modified writers use unique hash-verified backups and guarded restore outcomes. The backup index is in memory, there is no user-facing task-scoped undo manager, and retention/cleanup after process death still needs a durable design. Some unmodified executors may still use legacy backups.
- **External-writer race and shared mutation locks.** The new writer checks a SHA-256 fingerprint before committing and uses an atomic sibling-temp replacement where supported. This reduces, but cannot eliminate, a race with an external editor between the last check and filesystem move; cross-process file locking is not implemented.
- **Copy/move recovery edge cases.** Updated helpers verify source and destination fingerprints, reject symlinks, recheck source stability after copying, refuse to merge into a destination directory that appears concurrently, and preserve partial output rather than recursively deleting a destination that may contain concurrent user edits. Moves use default no-replacement semantics rather than `ATOMIC_MOVE`, whose provider-specific behavior can overwrite a destination created in a race. Filesystem-specific moves, interruption, insufficient storage, partial parent creation, and rollback failure still require device tests.
- **UI-visible design edits.** Layout helpers now check exact disk read-back and full `ViewBean.isEqual` parity—including widget type, IDs/parents and text/layout/image/settings attributes—after native synchronization. A device test must still prove the requested view is visibly present in the open designer, survives navigation/reopen, and stays consistent with generated sources/blocks. This has not yet been device-verified.
- **Build result observability.** The agent build tool now binds to a monotonically identified `BuildLogHub` session, waits for a terminal build event, and reports build success/failure/cancellation separately from install or runtime success. Device tests must still prove the listener catches every real build terminal path, timeout behavior is useful, and the reported APK can be installed/launched.
- **Tool argument validation.** The execution boundary validates required fields, types, enums, numeric bounds, collection/string limits, nested required fields, and unexpected top-level fields. Provider-specific malformed-JSON parsing and adversarial schema cases still require automated conformance tests.
- **Orchestrator termination.** Add explicit result states for step-budget exhaustion, timeout, cancellation, provider failure, tool failure, and partial completion. A final model response after the iteration cap must not be mistaken for task completion when it still requests tools.
- **Concurrency and cancellation.** Prevent overlapping runs from resetting a shared cancellation flag, reject or queue duplicate runs explicitly, propagate cancellation into active tools, and prevent late callbacks from a prior run updating a newer task.
- **Result provenance.** Tool results should carry structured status, path, operation ID, verification evidence, and error category. UI success indicators must derive from executor results and verification, not from model narration.
- **Secret hygiene and untrusted content.** Treat project files and tool outputs as untrusted data, redact credentials from diagnostics, cap output sizes, and ensure instructions embedded in project files cannot expand the executor's permissions.
- **Sketchware source of truth.** Determine whether each edit targets canonical project files, generated files, block metadata, or designer state. Prefer native Sketchware editing APIs for layouts/blocks where needed; validate the persisted artifact and the live editor model separately.


## Current candidate verification gate

Still unproven: clean Android compilation of the exact revision; temporary-project tests for create/edit/copy/move/delete/rollback; traversal/symlink/stale-hash/cross-project rejection tests; approval cancellation and Activity lifecycle tests; visible layout/widget updates in the live designer; every provider/model's actual tool calling; durable task resume/undo; and device-level verification that build terminal events and subsequent installation/launch results are accurately observed. The agent can report compile/sign terminal status from BuildLogHub, but does not yet verify install or runtime success.

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
- Build tool schema, project-bound dispatch, and terminal compile/sign reporting: code updated; Android compilation and on-device lifecycle/install tests still required.
- Full tool-by-tool acceptance matrix: not yet executed. No claim is made that all tools, visual updates, undo, or build-result reporting are complete.

### Follow-up audit: manager file placement, argument failures, and reasoning display

- **Confirmed root cause in managed source placement:** `java_create` previously appended the entire active package (for example, `com.example.app`) below the Java/Kotlin Manager root. `ManageJavaActivity` already starts at that root and computes package names from its `pkgName` extra plus folders the user navigates into. This caused base-package files to be placed under artificial nested package folders instead of at the visible manager root. The create tool now writes base-package files directly at the manager root and only creates the suffix folders for an explicitly requested subpackage. The generated project-source target still uses the full package path.
- **Mode-specific edit arguments:** `java_edit` and `res_edit` advertised `mode` while their schema did not express the conditional `content`/`search` requirements. Missing arguments could fail later as generic exceptions. Both executors now reject unsupported modes, missing content, and an empty/missing search pattern before resolving or mutating the target file.
- **Private reasoning disclosure:** the UI streamed provider reasoning tokens into the expandable thinking card and persisted them in chat history. New responses now display a fixed execution-status summary instead of raw reasoning, and the thinking-card renderer masks legacy saved reasoning too. This does not affect normal assistant text or structured tool execution events.
- **Tool-call path reviewed:** `ToolRegistry` is the shared source for both provider declarations and executor lookup; `AgentOrchestrator` validates arguments against the registered schema, checks the task-bound project ID, executes the registered tool, records the returned result against the original tool-call ID, and surfaces executor failures. OpenAI-compatible, Anthropic, and Gemini adapters were inspected for tool-call/result mapping. Malformed argument JSON becomes an empty object and is rejected by the executor-boundary schema validation rather than being treated as success.
- **Static review scope:** the registry was checked against the repository tree (all 40 registered executor source files exist), and the orchestration/schema boundary plus representative project, filesystem, Java/Kotlin, resource, asset, block, manifest, and library operations were inspected for the failure modes relevant to this report. This was not an exhaustive line-by-line audit of every executor or a runtime test suite. Follow-up tests remain necessary; code inspection is not proof of device behavior.


- **Active-project discovery:** `project_info` previously required the model to supply `sc_id`, even though the orchestrator already binds each run to the open project's ID. Its argument is now optional and defaults to `ctx.sc_id`; the result identifies the bound project. The system prompt explicitly tells the model to inspect that metadata instead of enumerating projects or asking the user to select the already-open project.
- **Nested source discovery:** `java_list` used `FileUtil.listDir`, which returns only immediate children, then filtered for `.java`/`.kt` files. It therefore omitted source files under package/subpackage folders. It now recursively lists Java/Kotlin files, validates every visited path against the active project, rejects symlinks, skips agent backups/trash, sorts results deterministically, and reports bounded/partial results.
- **Directory creation and navigation status:** `fs_mkdir` now distinguishes an existing directory from a file conflict, verifies newly created directories, and notifies the affected live manager. `open_manager` no longer reports that a screen was opened merely because a UI runnable was queued; it validates the active editor/type and returns an explicit navigation-request status without claiming screen visibility was verified.
- **Asset path contracts:** asset read/edit/delete/list schemas now specify that paths are relative to the assets root and must not repeat the `assets/` prefix, reducing duplicated-path tool calls.

- **Manifest edit argument validation:** `manifest_edit` now rejects unsupported operations and missing operation-specific `name`/`value` fields before reading or writing metadata, rather than allowing malformed calls to fall through to generic exceptions.
### Follow-up verification status

- Verified by repository read-back: the source-placement fix, edit-argument guards, and reasoning-display masking are present on `TAJ-STUDIO-AI`.
- Not run, as requested: no build was started.
- Not verified in this environment: Android compilation, live-device file visibility, provider-specific tool execution against each supported model, cancellation/approval races, and the complete destructive-operation acceptance matrix. Do not treat this static audit as a passing runtime test suite.


## MCP bridge foundation — 2026-10-22

- Added `agent/McpBridge.java` as an in-process JSON-RPC adapter over the existing `ToolRegistry`. It supports `initialize`, `ping`, `tools/list`, `resources/list`, `resources/read`, and read-only `tools/call`.
- The bridge uses the registered tool schemas and `ToolArgumentValidator`, checks that the original editor session and `sc_id` remain active before and after reads, and exposes project info/structure as MCP resources.
- **Fail-closed mutation policy:** `tools/call` refuses mutation tools. This is intentional: mutations must continue through `AgentOrchestrator` so its project-run lock, per-call approval, cancellation, and failure tracking remain authoritative. Do not remove this guard or add a second executor until those safeguards are factored into one shared execution gate.
- **Integration boundary:** this commit adds an in-process protocol adapter, not a network/stdin MCP server and not yet a connected external MCP transport. It is not evidence that a client can connect to the app. A transport and lifecycle owner still need to instantiate the bridge from the active editor/runtime.
- **Event-logic gap remains open:** the inspected `oq.getEventsForClass` / `ViewEvents` path visibly includes `onClick` for Clickable widgets, but this inspection did not establish end-to-end support for `onLongClick`, `onTouched`, `onFocused`, or `onScrolled` through event registration, block editing, code generation, and generated Java. Do not advertise these as supported or add names to the UI list alone. Each event needs an end-to-end capability test against the real event/code-generation pipeline.
- **Verification status:** source was committed and read back from GitHub. Android compilation, unit tests, MCP protocol conformance tests, external transport integration, and device acceptance have not yet been verified. This foundation must not be described as a complete MCP server or a completed visual-event implementation.


## MCP bridge control-surface correction — 2026-10-22

- Corrected `tools/list` to advertise only tools that the in-process bridge actually permits to execute. Previously it advertised the entire registry, including mutation tools that `tools/call` would reject; that was misleading to MCP clients.
- Read-only entries are marked with MCP tool annotations (`readOnlyHint`, `destructiveHint`, `idempotentHint`). The allowlist remains fail-closed: a missing registry entry is not advertised and an attempted non-allowlisted call is rejected.
- This is a discovery/contract correction, not full project control. The bridge still does not expose writes, does not have a transport, and is not wired to an external MCP client. Read operations are not yet coordinated through a shared project-wide lock with AgentOrchestrator. Do not describe this as complete MCP support.
- Required next implementation: create one shared tool execution gate used by both AgentOrchestrator and MCP, with active-project revalidation, argument validation, risk classification, per-call consent for destructive/overwrite operations, project-scoped mutual exclusion, cancellation/deadline handling, read-back/hash verification, and task-scoped undo evidence. Only after this shared gate is in place should MCP expose write-capable tools. Add protocol and concurrency tests before enabling the write surface.
