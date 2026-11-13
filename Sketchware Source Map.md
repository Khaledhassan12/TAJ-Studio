# Sketchware Source Map & Universal AI Engineering Skill

> **Purpose:** A portable engineering reference for developers and AI agents working with the original Sketchware Pro source, any version of Sketchware Pro, or a modified/community fork derived from it.
>
> **Primary source of truth:** [Official Sketchware Pro repository](https://github.com/Sketchware-Pro/Sketchware-Pro)  
> **Supplementary project-format reference:** [SWX_Skill — Sketchware Project System Reference & AI Skill](https://github.com/saqib-cipher/SWX_Skill)

This document is not tied to a particular repository, branch, package name, fork, user, device, Android SDK, build system version, or AI vendor. The shared Sketchware programming model is the conceptual foundation; every implementation-specific detail must be verified in the version being inspected.

**Compatibility rule:** Original and modified versions share core concepts—projects, screens, views, events, blocks, components, resources, code generation, and builds—but they may differ in classes, paths, schemas, opcode registries, encryption, dependencies, APIs, and build stages. Do not assume that internal implementation details are identical across versions.

---

## 0. Agent entry point — read this first

Sketchware Pro is a visual Android application development environment. A project is a structured programming model containing screens, view trees, logic blocks, variables, events, components, resources, and configuration. The application persists this model, transforms it into Android build inputs, and compiles an APK.

The most important engineering rule is:

**Preserve the complete project-model → persistence → generation → build/runtime contract.**

A change that looks correct in one editor screen can still corrupt saved projects, visual logic, generated Java/XML, resource linking, manifest entries, or the final APK.

### Required operating sequence

1. Identify the actual repository/project, current version, commit, branch, and working-tree state. Never assume these from a previous task.
2. Read the repository README, local agent instructions, relevant build files, and feature-specific documentation.
3. Trace the actual call path from UI entry point through model/state/persistence to code generation and build output.
4. Identify compatibility constraints: existing projects, SWB imports/exports, Android API levels, generated code, dependencies, and serialized data.
5. Make the smallest coherent change. Preserve APIs and serialized formats unless a migration is explicitly designed.
6. Add or update tests. For model/generator changes, test both the model output and generated artifact.
7. Run the narrowest relevant test first, then the build tasks supported by the checked-out repository.
8. Inspect the final diff for accidental changes, generated files, secrets, resource collisions, and unrelated edits.
9. Report exactly what ran and what did not. Never claim a successful build or generated artifact without evidence.

### Non-negotiable rules

- Do not replace native visual blocks with raw Java/source-text blocks merely because generating Java is easier. Native blocks must remain editable in the visual editor.
- Never use a reserved/sentinel block ID as a real block. In common Sketchware formats, block ID `0` is invalid; verify the target validator and allocator.
- Never write directly to encrypted project files as if they were plaintext. Use the target implementation's serializers/crypto adapters or a verified compatible implementation.
- Do not silently change SWB archive layout, project metadata types, event section naming, view hierarchy semantics, resource IDs, or generated-code conventions.
- Do not assume upstream and modified forks are interchangeable internally. Shared programming concepts do not guarantee identical implementation details.
- Do not remove functionality merely to make a build pass. Diagnose the failing layer.
- Do not commit API keys, OAuth tokens, Firebase production configuration, signing credentials, local SDK paths, or user project data.
- Avoid broad refactors while fixing a targeted defect. Keep the diff reviewable.
- Treat imported projects and custom code as untrusted input. Validate paths, archive entries, identifiers, XML, JSON, and generated source boundaries.

---

## 1. Repository and version discovery

This reference intentionally defines no fixed repository, working branch, package name, Android SDK, language level, or build configuration. Those facts belong to the checkout being worked on and must be discovered before implementation.

### Repository identity

Record:

- Repository root and remote URL(s).
- Current branch and HEAD commit.
- Whether the source is official upstream, a fork, or a source export.
- Upstream base commit or tag, where known.
- Local modifications and untracked files.
- App/module names, package/namespace, and application ID.
- Toolchain and build configuration.

Useful Git commands:

```bash
git rev-parse --show-toplevel
git status --short --branch
git rev-parse HEAD
git remote -v
git log -5 --oneline --decorate
```

Do not switch branches, reset, clean, or discard user changes unless explicitly authorized.

### Discover the actual build environment

Read the checked-out repository's README, contribution guide, Gradle wrapper, settings files, module build files, version catalog, build logic, and CI workflows.

Record the actual:

- Gradle and Android Gradle Plugin versions.
- Java/Kotlin versions and language targets.
- compileSdk, minSdk, and targetSdk.
- Application module(s), namespace, and application ID.
- Debug/release variants and signing configuration.
- Available unit, instrumentation, lint, and build tasks.
- Required environment variables and external services.

Do not copy toolchain values or build commands from another Sketchware version.

### Build commands

Discover available tasks first. For a Gradle-based checkout, `./gradlew tasks` can help enumerate them; use `gradlew.bat` on Windows. Execute only tasks actually provided by the repository, and report the exact commands and results.

A successful compile is not proof of runtime correctness. For editor, project import/export, generator, or build-system changes, perform focused fixture-based or runtime verification whenever possible.

### Secrets and signing

Keep API keys, OAuth tokens, production Firebase configuration, signing keys, and passwords outside source control. Inspect signing configuration before producing a release build. A test/debug keystore is not a production release identity. Never report a production-ready release unless signing and release validation have actually been verified.

---

## 2. System architecture — mental model

Think of Sketchware as a pipeline of cooperating subsystems:

```text
Android UI / Activities / Fragments
             |
             v
Editor interaction + adapters + dialogs + tools
             |
             v
Project model (screens, views, blocks, components, resources, settings)
             |
             +----------> Project persistence / workspace / backup
             |
             v
Code and resource generation
   |          |            |
   v          v            v
Manifest    Java/Kotlin    Layout XML + values/assets
generation  generation     generation
   \          |            /
    \         |           /
     v        v          v
       Android build / packaging pipeline
                    |
                    v
              APK / build logs
```

This is a conceptual dependency map, not a guarantee of exact runtime class boundaries. Trace real call sites in the current branch before editing.

### Main responsibility boundaries

| Subsystem | Owns | Typical failure surface |
|---|---|---|
| Project/workspace | Project identity, directories, open/close lifecycle, import/export | Missing projects, stale paths, damaged workspace |
| Screen/file model | Activities, fragments, custom views and screen metadata | Wrong screen class, invalid screen references |
| View editor/model | Widget tree, properties, IDs, layout hierarchy | Broken hierarchy, invalid XML, preview/editor mismatch |
| Logic editor/model | Events, variables, lists, functions and block graph | Invalid block IDs, broken graph edges, missing events |
| Components | Non-view objects and their listeners/configuration | Missing generated fields/listeners, wrong component type |
| Resource manager | Images, icons, fonts, sounds, strings and resource metadata | Missing assets, invalid names, AAPT2 failures |
| Project settings | SDK/build options, permissions, libraries and compiler flags | Build incompatibility, configuration drift |
| Generators | Manifest, Java/Kotlin, XML, resources and support files | Generated source does not compile or behave correctly |
| Build pipeline | Dependency resolution, compilation, D8/R8, packaging and signing | Build failures, runtime linkage errors |
| Backup/restore | SWB archive creation, extraction and migration | Data loss, path traversal, incompatible archive |
| Custom code integration | User Java/Kotlin, assets, native libraries, manifest/resource injection | Duplicate classes, unsafe paths, dependency conflicts |

**Architectural boundary:** editor UI should manipulate the project model through the established model/controller/persistence pathways. Do not put serialization, code-generation, or build rules into a view click listener when a shared domain/service layer already owns that behavior.

---

## 3. Upstream source map — known generator entry points

The upstream Sketchware Pro README publishes this concise source map. These responsibilities are useful navigation anchors, but confirm each class and its current implementation in the target checkout before changing it.

| Upstream class | Published responsibility | Investigate when |
|---|---|---|
| `a.a.a.ProjectBuilder` | Helper for compiling an entire project | Build orchestration, task ordering, compilation lifecycle |
| `a.a.a.Ix` | Generates `AndroidManifest.xml` | Activities, permissions, services, receivers, manifest merge |
| `a.a.a.Jx` | Generates Activity source code | Activity fields, lifecycle methods, variable declarations, event bodies |
| `a.a.a.Lx` | Generates component source such as listeners | Component fields, listener wiring, callbacks |
| `a.a.a.Ox` | Generates layout XML files | View hierarchy, layout attributes, generated XML |
| `a.a.a.qq` | Registry of built-in library dependencies | Built-in dependency declarations and project library selection |
| `a.a.a.tq` | Organizes compiling-dialog quizzes | Build dialog behavior only; not the compiler itself |
| `a.a.a.yq` | Organizes Sketchware project file paths | Project storage paths and generated file locations |

The upstream README also points contributors to the `mod` package as a major location for community modifications.

### How to locate a class safely

Use repository search rather than assuming a path:

```bash
git grep -n "class ProjectBuilder"
git grep -n "class DesignActivity"
git grep -n "class BlockBean"
git grep -n "class ViewBean"
git grep -n "ProjectBuilder"
```

For PowerShell:

```powershell
Get-ChildItem -Recurse -File app/src | Select-String -Pattern "class ProjectBuilder"
```

Then inspect:
- declaration and superclass/interfaces;
- constructors and injected dependencies;
- all callers and overrides;
- read/write sites for the model fields;
- lifecycle ownership;
- tests and fixtures;
- upstream history where behavior is unclear.

Obfuscated package/class names (for example `a.a.a.*`) are not a license to infer behavior from the name. Follow call graphs and observable data flow.

---

## 4. Sketchware project data model

A Sketchware project is not simply a folder of Java files. It is a structured model which the app reads, edits, persists, exports, and compiles.

The canonical SWX reference describes a common workspace layout similar to the following. Actual roots and optional files vary by fork/version and device; use the project's workspace/path resolver rather than hardcoding a storage path.

```text
.sketchware/
├── data/
│   ├── system/                    # Global event/listener/component definitions
│   └── <sc_id>/
│       ├── project                # Core project metadata (encrypted in common format)
│       ├── file                   # Screen/activity definitions
│       ├── logic                  # Events, variables, lists, functions, blocks
│       ├── view                   # Widget hierarchy and properties
│       ├── resource               # Resource registry
│       ├── library                # Project library configuration
│       ├── project_config         # Extended build settings (often plaintext JSON)
│       ├── DataDayDream.json      # Optional DayDream/native configuration
│       ├── custom_blocks          # Custom block definitions
│       ├── permission             # Declared permissions
│       ├── import                 # Injected Java imports
│       ├── proguard-rules.pro     # Project shrinker rules
│       └── files/
│           ├── java/              # Custom Java/Kotlin sources
│           ├── assets/            # APK assets
│           ├── resource/          # Custom Android resources
│           ├── native_libs/       # Native .so libraries
│           ├── broadcast/        # Optional receiver sources
│           ├── service/          # Optional service sources
│           └── library/          # Optional project-local libraries
├── mysc/                          # Project list/cache/display data
├── bak/                           # Optional editor/session backups
├── resources/                     # Shared project media/resources
└── libs/local_libs/               # Optional global local-library store
```

### Data ownership and consistency

- `project`: project identity and app metadata (name, package, version, colors, timestamps, type).
- `file`: screen/activity/fragment/custom-view declarations and associated metadata.
- `logic`: event declarations, variables, lists, functions and block sequences.
- `view`: visual widget hierarchy, view properties, dimensions and layout attributes.
- `resource`: resource registrations and references.
- `library`: dependency/library selections.
- `project_config`: compiler/build settings such as SDK levels and build-system options.
- `permission`, `import`, custom code and asset directories: project-specific extensions to generated Android output.

A project is valid only when references across these files agree. For example, a block that refers to a missing view, a component whose generated listener is absent, or a resource entry with no corresponding file can break editing or compilation.

### Metadata typing

The SWX reference documents a compatibility-sensitive rule: fields such as project version codes/names may be read as strings by existing Sketchware code. Do not normalize numeric-looking strings into JSON numbers without tracing every reader and providing a migration. Preserve original JSON types when round-tripping.

### Storage path rule

Never concatenate `/storage/emulated/0/.sketchware` into new code. Resolve the active workspace through the project's existing path abstraction. Android scoped-storage behavior and fork-specific workspace configuration make hardcoded paths fragile.

---

## 5. Encryption and persistence

The common Sketchware project format encrypts core files such as `project`, `file`, `logic`, `view`, `resource`, and `library`. The SWX reference documents AES-128-CBC with PKCS padding and the compatibility key/IV used by the format (`sketchwaresecure`). This is a legacy file-format compatibility mechanism, **not modern protection for user secrets**: the key is embedded/known in compatible implementations.

Operational requirements:

1. Identify the exact serializer, cipher helper, charset, and file writer used by the current branch.
2. Read/decrypt through that implementation; do not write plaintext JSON into an encrypted file.
3. Preserve byte encoding and line/section conventions.
4. Write atomically where the existing storage layer supports it: temporary output → validate → replace.
5. Keep a recoverable original until the replacement has passed parsing/validation.
6. Handle truncated, empty, malformed, and unsupported-version files without silently overwriting them.
7. Do not log project contents, credentials, private source, or decrypted data unnecessarily.
8. Test round-trip behavior: decrypt → parse → serialize → encrypt → decrypt → compare semantic content.

Never use the legacy project-file cipher to store OAuth tokens, passwords, API keys, or other new secrets. Use Android Keystore-backed mechanisms or the platform's approved secret-storage design.

---

## 6. Logic model and block graph

The `logic` data file is section-oriented. In the common format, a section begins with an `@` header, followed by lines describing variables or JSON block records.

Common section families include:

| Section pattern | Meaning |
|---|---|
| `@<JavaName>_var` | Activity-scoped variables |
| `@<JavaName>_list` | Activity-scoped lists |
| `@<JavaName>_func` | MoreBlocks/custom function declarations |
| `@<JavaName>_components` | Component instances |
| `@<JavaName>_events` | Event registry |
| `@<JavaName>_<viewId>_<eventName>` | Blocks for a view event |
| `@<JavaName>_onCreate_initializeLogic` | Activity initialization logic |
| `@<JavaName>_<function>_<function>` | MoreBlock body in the documented format |

Exact section names and event behavior are version-sensitive. Preserve the existing writer/parser's naming conventions.

### Block record anatomy

A common BlockBean JSON record has fields similar to:

```json
{
  "color": -16354097,
  "id": "10",
  "nextBlock": 11,
  "opCode": "setVisible",
  "parameters": ["textview1", "gone"],
  "spec": "%m.view setVisible %m.visible",
  "subStack1": -1,
  "subStack2": -1,
  "type": " ",
  "typeName": "setVisible"
}
```

| Field | Responsibility |
|---|---|
| `id` | Unique block identity; must not be `0` |
| `nextBlock` | Next statement in the same sequence; `-1` means no next block |
| `opCode` | Generator/operation identifier |
| `parameters` | Ordered socket values or reporter references |
| `spec` | Display/input specification used by the block system |
| `subStack1` | First nested statement stack |
| `subStack2` | Second nested statement stack (for constructs such as else) |
| `type` | Output/connection type |
| `typeName` | Type identity used by the block system |
| `color` | Palette/category color metadata |

### Graph invariants

- IDs are unique within the scope expected by the current editor/parser and are never zero.
- Every non-terminal `nextBlock` reference resolves to an existing compatible block.
- Nested stack references resolve to valid stack roots.
- Reporter references use the documented `@<id>` form and point to a compatible output type.
- Statement chains do not contain accidental cycles.
- A block is not shared by unrelated parents unless the engine explicitly supports that graph shape.
- Parameter count/order/types match the opcode's registered specification.
- Deleting or replacing a block must repair incoming/outgoing references and nested stacks.
- Generate native BlockBean structures; do not substitute source text for editable logic.

### Variables and lists

The SWX reference documents common variable types:
- `0`: boolean
- `1`: number represented as Java `double`
- `2`: String
- `3`: map / `HashMap<String, Object>`
- `6`: custom field declaration
- `9`: import injection

Common list types:
- `1`: number list
- `2`: string list
- `3`: map list

Identifiers are constrained by the Sketchware validators and may conflict with Java keywords, engine symbols, view IDs, or component IDs. Always invoke the project's existing validator; do not reproduce a partial validator in a new UI layer.

### Events

The common format distinguishes view, component, activity, drawer-view, and custom-view events. Activity initialization logic is commonly stored in its dedicated section and injected by the engine; duplicating it in the event registry can create duplicate event cards. Verify the current event loader before adding lifecycle entries.

### MoreBlocks

MoreBlocks are user-defined reusable block functions. Their declaration/specification, body section, argument reporter blocks, and invocation blocks must agree exactly. Preserve the argument order and type contract. Verify the current implementation of `definedFunc`, `getArg`, and function generation before generating or editing these structures.

---

## 7. View tree and layout model

The `view` data represents a tree, not a flat list. Each widget has a type, identifier, parent/child relationships, and a set of properties. The generator converts this model into Android layout XML; the preview/editor consumes the same or a related model.

### Required invariants

- Each view ID is valid and unique in its expected screen scope.
- Parent/child relationships form a valid tree (no cycles, no orphaned children unless explicitly supported).
- Child order is preserved.
- Layout parameters are valid for the actual parent type. LinearLayout, RelativeLayout, FrameLayout, and other parents do not share identical layout-param semantics.
- Width/height values and units must be supported by the serializer and XML generator.
- Margins, padding, gravity, orientation, weight, visibility, and background values must preserve their meaning.
- A widget type must map to a supported ViewBean/type registration and a valid Android class.
- Custom view conversion/injection behavior must be traced before use.
- Resource references must resolve and use valid Android resource names/types.

### XML and AAPT2 safety

Do not blindly inject raw XML attributes. Validate attribute names, values, namespaces, and context. Avoid malformed color values, invalid resource identifiers, duplicate attributes, and unsupported dimension syntax. Build-generated XML is an important test artifact: inspect it when a view-generation change fails.

### Responsive qualifiers

The SWX reference describes qualifier-aware layouts in SketchwareX/DayGreen variants (for example default, landscape, and sw-qualified layouts). Do not assume this feature exists in the target version or fork. Check the actual parser, storage format, and generator before implementing or relying on responsive qualifiers.

---

## 8. Components and listeners

Components are non-visual objects associated with a screen (for example Intent, SharedPreferences/File, Calendar, Vibrator, Timer, Dialog, BottomSheetDialog, MediaPlayer, SoundPool, ObjectAnimator, RequestNetwork, text-to-speech/speech-to-text, and Firebase-related components).

A component typically has:
- a stable component ID;
- a component type;
- configuration/properties;
- generated Java field/initialization;
- optional event/listener registration;
- cleanup/lifecycle behavior.

The component registry, editor palette, persistence model, and code generator must agree. Adding a component is not complete when it merely appears in a picker. Verify serialization, restoration, generated declarations, initialization, listener callbacks, lifecycle cleanup, and build dependencies.

Use the current branch's component type registry as the source of truth. Do not copy numeric type IDs from another fork without confirming collisions and mappings.

---

## 9. Resource management

Resources include images, icons, fonts, sounds, strings, vector assets, and custom Android resources. The resource model often contains both a registry entry and a physical file.

When adding or changing resource behavior, verify all of the following:

1. Import path and filename normalization.
2. Resource-type mapping and legal Android naming rules.
3. Duplicate-name and collision behavior.
4. Registry serialization and project restore.
5. Correct generated destination directory.
6. Correct XML/code reference.
7. Density/qualifier behavior where supported.
8. AAPT2/resource linking.
9. SWB packaging and extraction.
10. Cleanup when a resource is deleted or renamed.

For localized strings, preserve the repository's resource conventions and escaping rules. Do not create hardcoded UI text when an existing strings resource mechanism is expected. Never overwrite user resources silently.

---

## 10. Libraries, custom code, and native files

Project libraries can affect Gradle dependencies, manifests, resources, ProGuard/R8 rules, and native ABI packaging. Custom code can introduce imports, classes, services, receivers, assets, and Android resources.

Before modifying library integration:
- trace how library metadata is stored and restored;
- locate dependency registry/resolution code;
- inspect generated Gradle files or build inputs;
- verify transitive dependency conflicts and duplicate classes;
- verify manifest/resource merging;
- preserve consumer shrinker rules;
- inspect native ABI directories and packaging;
- test clean and incremental builds.

Never assume that adding an AAR/JAR to a directory automatically makes it available to the compiler. Follow the repository's actual library discovery and build integration.

Custom source paths must be normalized and confined to the project workspace. Reject traversal segments, absolute paths, symlinks escaping the workspace, and archive entries that attempt to write outside the extraction root.

---

## 11. Build and compilation pipeline

At a high level, a Sketchware project build transforms the project model into Android build inputs:

1. Resolve project identity and settings.
2. Validate project structure and required files.
3. Collect selected screens, components, libraries, permissions, custom source and resources.
4. Generate/merge the Android manifest.
5. Generate Activity/component Java source.
6. Generate layout XML and resource files.
7. Resolve libraries and compile source.
8. Run Android resource processing and linking (AAPT2).
9. Convert/merge bytecode (D8 and related tools).
10. Apply configured shrinking/obfuscation when enabled.
11. Package, align and sign the APK.
12. Persist build logs/results and expose output to the user.

This is a conceptual pipeline. Confirm the actual task graph and ordering in the current branch's `ProjectBuilder`, build services, Gradle tasks, and receivers.

### Generator debugging strategy

When a build fails, classify it before editing:

| Symptom | First area to inspect |
|---|---|
| Missing/invalid manifest entry | Manifest generator and project permissions/screen declarations |
| Java symbol or method missing | Activity/component source generator and imports |
| Listener not firing | Event registry, component/view listener generation |
| XML parse/AAPT2 error | View-to-XML generator and resource values |
| Missing resource | Resource registry, file copy, generated reference |
| Duplicate class/dependency | Library registry and Gradle dependency resolution |
| D8/R8 failure | Bytecode inputs, keep rules, dependency compatibility |
| APK installs but crashes | Runtime generated code, Android API assumptions, manifest |
| Build works only incrementally | stale generated outputs/cache/task dependencies |

Do not “fix” a generated-source problem by editing generated output only. Find and repair the generator or model source; generated output is evidence, not the durable source of truth.

---

## 12. SWB backup, import, and restore

A `.swb` file is commonly a ZIP-based Sketchware project backup. The SWX reference describes a package containing project metadata, data files, resource media, and optional local libraries. Exact archive contents differ between versions.

### Archive safety rules

- Treat every archive entry as untrusted.
- Reject absolute paths, `..` traversal, drive prefixes, NUL bytes, and paths escaping the destination.
- Limit entry count, expanded size, per-file size, and compression ratio to prevent resource exhaustion.
- Do not trust archive-provided project IDs or destination paths.
- Validate required files and metadata before registering the restored project.
- Preserve encryption format and required directory structure.
- Avoid partial restore: extract to a staging directory, validate, then atomically promote.
- Never delete an existing project before the replacement has passed validation.
- Test import/export round trips and compatibility with representative older SWB files.

### Export compatibility

The exported archive must match the format expected by the target Sketchware version, not merely be a ZIP that contains source files. Compare actual export implementation and fixtures. Preserve directory names, encryption state, metadata, media, and optional libraries.

---

## 13. Testing strategy

Choose tests by layer. A green APK build alone is insufficient for project-system changes.

| Change area | Minimum useful verification |
|---|---|
| Project metadata | Parse/serialize round-trip; legacy fixture compatibility |
| Crypto/persistence | Encrypt/decrypt round-trip; malformed input; atomic write |
| Logic blocks | Block graph validation; native block editor load; generated Java compile |
| Events/components | Save/reopen; listener generation; runtime callback |
| View/layout | Save/reopen; preview; generated XML parse; AAPT2 |
| Resources | Import/rename/delete; registry consistency; resource linking |
| Libraries | Dependency resolution; clean build; duplicate-class check |
| SWB import/export | Round-trip fixtures; traversal and oversized archive tests |
| Build pipeline | Clean build and incremental build; inspect logs/artifacts |
| UI | Lifecycle, rotation/recreation where relevant, empty/loading/error states |

### Regression fixtures worth maintaining

- Minimal empty project.
- Project with multiple Activities.
- Nested view hierarchy with varied layout params.
- Each supported component family.
- Logic with chained statements, nested stacks, reporter blocks, and MoreBlocks.
- Custom Java/Kotlin, assets, resources, permissions, and libraries.
- Legacy project metadata and SWB versions.
- Corrupt/truncated project files and invalid archive entries.

---

## 14. Safe modification playbooks

### A. Add a new visual widget

1. Find the view type registry and existing analogous widget.
2. Trace palette/picker → ViewBean creation → property editor → serialization → preview → XML generator.
3. Add type-specific defaults and validation.
4. Add resource/string entries using existing conventions.
5. Confirm parent layout parameters and child ordering.
6. Test save/reopen, preview, generated XML, and AAPT2.
7. Verify old projects remain readable.

### B. Add a new logic block

1. Identify the block definition/opcode registry and palette category.
2. Define exact input sockets, output type, color/category, and parameter order.
3. Implement native block model/editor support.
4. Implement code generation for the opcode.
5. Validate IDs, references, nesting, and type compatibility.
6. Add a fixture with the block in a real event and nested/reporter contexts.
7. Confirm the generated Java compiles and the block remains editable after reopening.

### C. Add a component

1. Locate component type registration and component bean/model.
2. Add creation/configuration UI.
3. Add persistence and restoration.
4. Add generated field/initialization/listener code.
5. Add lifecycle cleanup where needed.
6. Register required dependencies and shrinker rules.
7. Test save/reopen, generation, build, and runtime callback.

### D. Change project storage or SWB

1. Inventory every reader/writer/import/export/backup path.
2. Define compatibility and migration behavior before changing schema.
3. Use versioned migration where possible; preserve unknown fields.
4. Implement staging, validation, and rollback.
5. Test old and new fixtures plus malformed input.
6. Confirm no user data is overwritten or lost.

### E. Change compiler/build behavior

1. Capture a reproducible failing project and full build log.
2. Identify the first failing task, not just the final Gradle error.
3. Trace generated inputs and dependency resolution.
4. Fix the source of the bad input.
5. Test clean and incremental builds.
6. Install/run the APK if the change affects generated application behavior.

---

## 15. Common failure patterns and their root causes

| Failure | Likely underlying issue | Correct response |
|---|---|---|
| Project appears in list but cannot open | Project metadata/path mismatch or navigation/open lifecycle failure | Trace list adapter click through project resolver and editor launch |
| Editor opens but blocks are missing | Logic section parsing, event keys, block graph, or crypto issue | Compare decrypted model before/after save and inspect parser |
| Duplicate initialization event | Activity lifecycle event injected both by engine and persisted registry | Inspect event loader and keep one authoritative registration |
| Invalid block ID 0 | Generator/importer allocated sentinel ID as real block | Allocate valid unique IDs and validate all references |
| Block visible but generated app lacks behavior | UI registration exists but opcode generator is missing/wrong | Trace opcode to generator and test emitted Java |
| Preview differs from built APK | Preview parser and XML generator disagree | Compare model interpretation and generated XML |
| AAPT2 resource error | Invalid name/value, missing file, duplicate resource or malformed XML | Inspect generated resources and resource registry |
| Works on one fork, fails on another | Fork-specific IDs, schema, APIs, or generator divergence | Verify the target branch implementation; do not transplant assumptions |
| Build only passes after clean | stale outputs, missing task dependency, cache invalidation issue | Fix task inputs/outputs and dependency graph |
| SWB import damages existing project | direct extraction, missing validation, or non-atomic replacement | Stage, validate, and atomically promote |

---

## 16. Cross-version and fork adaptation protocol

Use this protocol for every target that is old, modified, partially merged, or different from the official upstream repository.

1. Identify the exact target commit, version markers, and known upstream base, if available.
2. Establish the shared Sketchware programming concepts used by the feature: project, screen, view, event, block, component, resource, or build stage.
3. Inspect the target implementation and trace actual callers, writers, readers, serializers, validators, and generators.
4. Compare only the relevant files and behavior with the official upstream source. Do not assume that matching class names guarantee matching behavior.
5. Record differences in package paths, data schemas, opcode names, numeric IDs, encryption, storage paths, APIs, dependencies, and build steps.
6. Determine whether the target feature is present, renamed, partially implemented, disabled, or replaced by another abstraction.
7. Adapt the implementation to the target's existing architecture instead of forcing an upstream implementation onto it.
8. Preserve unknown fields and project data when safe. Add explicit migrations for incompatible schema changes.
9. Add tests or fixtures that verify the target behavior and, where applicable, compatibility with projects from earlier versions.
10. Document important verified divergence with the target version/commit so future agents do not mistake it for universal behavior.

**Important distinction:** The core visual programming model may be shared across Sketchware versions, but this does not make every internal file format or implementation detail identical. Reuse concepts broadly; reuse implementation details only after verification.

---

## 17. AI agent task contract

For every implementation task, produce this internal work plan before editing:

- **Requested behavior:** observable user-facing result.
- **Source of truth:** exact classes/files found in the active branch.
- **Call path:** entry point → model/service → persistence/generator/build.
- **Compatibility surface:** existing projects, API levels, dependencies, generated code and exports.
- **Change set:** smallest files/modules required.
- **Validation:** tests/build/runtime evidence.
- **Risks:** migration, data loss, resource/API breakage, lifecycle or security concerns.

When reporting completion, include:

1. Files changed and why.
2. Architecture/call path touched.
3. Tests and exact Gradle commands executed.
4. Build result and APK path only if it was actually produced and verified.
5. Known limitations or untested behavior.
6. Commit SHA/branch only if a commit was actually created.

Never claim “error-free,” “fully tested,” “build successful,” or “APK generated” without evidence. If the environment lacks Android SDK, network, secrets, device, or required test infrastructure, state that limitation and complete all feasible static checks.

### Prompt for an agent starting a new task

> Read `SKILL.md` and `Sketchware Source Map.md` completely before modifying a Sketchware repository. Treat it as an architectural index and operational contract, not a substitute for source inspection. Work on the explicitly requested branch. Verify every class, method, path, and behavior against the current checkout. Preserve Sketchware project-model compatibility and native visual-block fidelity. Trace changes through persistence, code/resource generation, and build/runtime behavior. Make the smallest safe change, add regression coverage, run relevant tests/builds, inspect the diff, and report evidence without inventing results. Use the linked SWX_Skill for deeper project-file grammar and SWB format details, and the upstream Sketchware Pro repository for original source responsibilities. If this document conflicts with the checked-out code, the current code and tests are authoritative; update this map after confirming the new behavior.

---

## 18. Reference sources and precedence

1. **Official Sketchware Pro source repository:** original source, upstream architecture, published generator map, build/contribution conventions, and history.  
   https://github.com/Sketchware-Pro/Sketchware-Pro
2. **SWX_Skill — Sketchware Project System Reference & AI Skill:** supplementary documentation for project storage, common encryption behavior, logic/block grammar, view/component/resource models, build pipeline, and SWB format.  
   https://github.com/saqib-cipher/SWX_Skill

### Source precedence when references disagree

1. The exact implementation in the target checkout determines what that checkout does.
2. Tests, fixtures, generated artifacts, and runtime observations establish what the implementation actually supports.
3. The official Sketchware Pro source establishes upstream behavior and inherited design intent.
4. Other forks and external skills provide supplementary investigative leads; verify their assumptions before reuse.
5. This map provides portable concepts and safe operating procedures.

Do not copy behavior, IDs, paths, or schemas from another fork merely because the programming model looks similar. Inspect the target, compare relevant implementation with upstream, and document verified differences.

---

## 19. Maintenance policy

Update this document whenever a change materially alters:
- module/package structure or major architectural boundaries;
- project storage schema, encryption, or migration;
- block/view/component/resource serialization;
- generator entry points or build pipeline;
- SWB import/export compatibility;
- SDK/toolchain/build commands;
- test fixtures or required validation.

Keep verified facts separate from hypotheses. Add the source file path and the date/commit where a newly documented behavior was verified. Remove stale instructions rather than accumulating contradictory notes.

**Goal:** an agent should spend its time solving the requested engineering problem—not rediscovering Sketchware's project model, generator responsibilities, compatibility constraints, or safe development workflow.
