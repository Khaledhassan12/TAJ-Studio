# AI Manager — Feature Ledger
> Permanent compass for the AI Manager feature across all agents, sessions and
> humans. Append-only: past entries are NEVER deleted. Updated at the START and
> END of every prompt that touches this feature.
> Created: 2026-08-08  |  Last updated: 2026-08-08 (P0-A creation)
> Status: 🟡 NOT YET IMPLEMENTING — architecture locked, awaiting first code round.

<!-- 
هذا الدفتر هو البوصلة الدائمة لميزة AI Manager، يسجل القرارات والمخاطر والتقدم لضمان الاستمرارية بين الوكلاء والمطورين.
This ledger is the permanent compass for the AI Manager feature, recording decisions, risks, and progress to ensure continuity across agents and developers.
-->

## 1. Identity
- Feature name: AI Manager (Local AI Model Manager + Runtime + Agent Layer)
- Package root: `pro.sketchware.ai.*`
- Owner file (this ledger): `AI_MANAGER_LEDGER.md`
- UI entry: AppSettings → "Artificial Intelligence Manager" → AiManagerActivity
- Runtime entry (hidden until configured): DesignActivity → Assistant Tab

## 2. Architecture (locked — do not change without explicit user approval)
TAJ Studio
└── AI Infrastructure
├── Provider Layer (unified AiProvider interface)
│ ├── Local: LlamaProvider → JNI → llama.cpp (GGUF)
│ └── Cloud: OpenAI / Anthropic / Gemini / OpenAI-Compatible
├── Model Management
│ ├── HuggingFace search (public by default, optional token for gated)
│ ├── DownloadManager (resume, verify, atomic finalize)
│ ├── GgufValidator (Java-pure: magic + architecture + metadata)
│ ├── Local import (existing .gguf on device)
│ └── ModelCatalog (unified local + cloud entries)
├── TAJ AI Core
│ ├── SystemPromptManager (single composition point)
│ ├── PromptComposer (unified → provider-specific adapter)
│ ├── ConversationManager (per-project, persistent)
│ ├── ProjectContextManager (context budget + retrieval)
│ ├── ToolRegistry + Tools (readFile / writeFile / createFile / deleteFile /
│ │ listFiles / searchProject / searchSymbol / patchFile / readBuildError /
│ │ runBuild / inspectProject)
│ └── AgentManager + AgentLoop (provider-independent)
├── Native Runtime
│ ├── java-llama.cpp (preferred) — fallback: self-built CMake + NDK
│ ├── Isolated :ai_runtime process for native crash containment
│ └── JNI surface: load / infer / cancel / unload
└── Storage
├── .sketchware/ai/models/*.gguf
├── .sketchware/ai/projects/<scId>/ (conversations, context, history)
└── AiDatabase (SQLite, no Room) + EncryptedSharedPreferences for keys

## 3. Implementation Log (append-only; every prompt adds an entry)

### P0-A  |  2026-08-08  |  Ledger creation
- Action: Created this ledger file with locked architecture.
- Files touched: `AI_MANAGER_LEDGER.md` (new), `TODO_AGENT.md` (append).
- What is NOT done yet: everything else (HF client, downloader, validator,
  runtime, providers, UI, tools, agent, prompts).
- Blocking items: none.
- Next round: **P0-B — package skeleton + AiDatabase + SecureKeyStore +
  Storage paths + AppSettings entry gate**.

## 4. Decisions Log (append-only; record every non-trivial decision)

### D1 — 2026-08-08 — java-llama.cpp preferred over self-built JNI
- Why: Proven Android binding maintained on Maven Central; saves months of JNI work.
- Fallback: If ABI coverage fails in P2, switch to self-built CMake + NDK
  (documented in llama.cpp official android.md).
- Owner: AI Lead (user + Qwen).

### D2 — 2026-08-08 — Isolated `:ai_runtime` process
- Why: Native crashes in llama.cpp kill the runtime process only, never the app.
- Cost: Cross-process messaging (AIDL/Messenger) for token streaming.

### D3 — 2026-08-08 — SQLite (no Room)
- Why: Project does not use Room; adding annotation processing is an unjustified
  build risk. Plain SQLiteOpenHelper + small DAO is sufficient.

### D4 — 2026-08-08 — HF public by default, token optional
- Why: Matches user spec and PocketPal AI reference; gated models still reachable
  via optional token stored in EncryptedSharedPreferences.

### D5 — 2026-08-08 — Agent tools are provider-independent
- Why: Local models (small) may not support native tool calling; structured tool
  protocol (JSON in fenced block + tolerant parser + single self-correction) is
  the universal fallback. Never invent capabilities.

### D6 — 2026-08-08 — System Prompts are NOT invented by the agent
- Why: User will supply the original default system prompts himself.
- When needed: at P4 (SystemPromptManager implementation), STOP the prompt and
  ask the user to send them. Do not fabricate.

## 5. Risks & Gotchas (append-only; warn future prompts)

### RISK-1 — Native ABI coverage
- Symptom: AAR missing an ABI → UnsatisfiedLinkError at runtime.
- Guard (P2): verify ABI presence in AAR BEFORE committing to it; document the
  fallback CMake build.

### RISK-2 — "Installed" lies (same class as the Marketplace B1 bug)
- Symptom: UI says a model is loaded, file is actually partial.
- Guard: double verification (size + GGUF magic) on every state transition;
  single-writer on state (R5).

### RISK-3 — Context overflow on small local models
- Symptom: Model chokes on the first prompt.
- Guard: ContextBudget reads `caps.contextSize` of the active model and trims
  project context + history before composition; never send the whole project.

### RISK-4 — Keys in plaintext
- Symptom: HF token or cloud API keys logged or written to disk.
- Guard: grep-ban in every round — no plaintext key writes, no key logging,
  EncryptedSharedPreferences only.

### RISK-5 — Fabricated capabilities
- Symptom: Agent claims a small local model supports native tool calling.
- Guard: CapabilityProfile per model; if unsupported → structured protocol only.

### RISK-6 — Losing context across sessions
- Symptom: Agent forgets what it did yesterday in the same project.
- Guard: Persistent per-project storage (P1 rule); conversation + agent steps
  written incrementally; survive process death and reboot.

## 6. Pending Decisions (must be answered by the user before the round uses them)

| ID  | Question                                                    | Needed by | Status   |
|-----|-------------------------------------------------------------|-----------|----------|
| PD1 | Original default System Prompts (the user's own)            | P4        | WAITING  |
| PD2 | Final ABI set for the released APK (arm64 only vs +x86_64) | P2        | WAITING  |
| PD3 | Assistant Tab icon (vector)                                 | P5        | WAITING  |

## 7. Next Step Pointer
- The NEXT prompt after this one must be **P0-B** (package skeleton + storage +
  SecureKeyStore + AiDatabase + AppSettings entry gate).
- Before writing any P0-B code, the agent MUST re-read this ledger top-to-bottom
  and append a new "P0-B started" entry at the top of §3.

## 8. Golden Rule for this feature
Every prompt that touches AI Manager MUST:
1. Re-read this ledger first.
2. Append a "started" entry to §3 before any code.
3. Append a "finished" entry to §3 after the round closes, including: files
   touched, tests run, results (pass/fail), and any new risk discovered.
4. Never delete or rewrite any previous entry in §3, §4, §5.
