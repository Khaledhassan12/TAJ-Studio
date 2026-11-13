---
name: sketchware-pro-engineering
description: Understand, inspect, create, import, export, debug, test, and safely modify Sketchware, Sketchware Pro, and Sketchware-derived Android IDEs across original, modified, and forked versions.
---

# Sketchware Pro Engineering

## Mission

Use this skill whenever a task involves Sketchware, Sketchware Pro, a modified build, a community fork, or a project created by one of these tools.

The objective is to help an AI coding agent understand the actual implementation in front of it and make correct, minimal, verifiable changes. This skill is repository-independent: it does not prescribe a particular fork, owner, branch, package name, application ID, Android SDK, build-system version, storage directory, or source layout.

Sketchware-derived tools share a recognizable programming model: a visual project is represented as structured data, edited through an IDE, transformed by generators into Android source and resources, and then built into an installable application. That shared model is the stable conceptual foundation. It does **not** mean every version has identical source code, class names, serialization details, encryption, archive layout, APIs, or build pipeline. Discover those details from the actual target before making assumptions.

## Portable loading

This is a self-contained Agent Skills-style entry point. Keep this file in a directory named for the skill when using an agent that supports the Agent Skills convention. For an agent that does not implement that convention, provide this complete file as project instructions or attach it as task context and ask the agent to follow it. No vendor-specific tool, extension, model, account, or repository is required.

The companion [Sketchware Source Map.md](Sketchware%20Source%20Map.md) adds a deeper functional map and investigation reference. It is useful context, but this SKILL.md contains the core operating rules and can be used on its own.

## Core operating rules

1. Inspect first. Never assume which fork, version, branch, module, source path, schema, or build task is present.
2. Treat the checked-out repository and its tests/fixtures as the implementation truth for the target being changed.
3. Use the official Sketchware Pro repository as the primary upstream reference: https://github.com/Sketchware-Pro/Sketchware-Pro
4. Preserve the entire project contract: editor model, persistence, generators, resources, build integration, import/export, and runtime behavior.
5. Prefer the smallest coherent change. Avoid unrelated refactors, speculative abstractions, and broad rewrites.
6. Preserve existing project data and backward compatibility unless the task explicitly requires a migration.
7. Never invent source paths, method names, resource identifiers, format fields, test results, or build outcomes.
8. Validate changes at the narrowest useful layer, then at the integration level.
9. Treat imported projects, archives, custom code, XML, JSON, and resource files as untrusted input.
10. Report what was inspected, what changed, what was tested, and what remains uncertain.

## 1. When to activate this skill

Activate it for tasks such as:

- Understanding a Sketchware or Sketchware Pro repository or project.
- Mapping source code, editor architecture, project storage, or code generation.
- Creating or editing visual blocks, components, views, screens, resources, or libraries.
- Implementing custom code support or changes to generated Android projects.
- Debugging project opening, saving, importing, exporting, compiling, or runtime behavior.
- Creating, inspecting, validating, or repairing SWB-like project archives.
- Porting a feature between upstream and a fork.
- Comparing original and modified versions.
- Building tests, compatibility checks, migration tools, or developer documentation.

Do not assume the user is working with a particular repository simply because the skill itself was installed inside another repository. The target project is the one the user identifies or the current working directory clearly establishes.

## 2. Universal discovery protocol

Before editing, build a compact target profile. Use repository inspection, Git history, source search, Gradle files, manifests, tests, fixtures, and sample projects. Do not spend time exhaustively reading unrelated code.

### 2.1 Identify the target

Record only facts you can verify:

- Repository root and remote URLs, when available.
- Current branch, commit, tag, and working-tree state.
- Product name, fork identity, version string, and version code, if present.
- Application ID, namespace, and Java/Kotlin package roots.
- Android modules and their responsibilities.
- Java/Kotlin versions, Gradle wrapper, Android Gradle Plugin, compile SDK, target SDK, and minimum SDK.
- Supported device/API range and known runtime restrictions.
- Main project model classes and persistence entry points.
- Source generators and build orchestration entry points.
- Import/export implementation and representative project fixtures.
- Test frameworks and existing build/test commands.

Do not infer the product version from a Git branch name. Do not infer application identity from a package directory alone. Check the manifest, Gradle configuration, generated metadata, and runtime code.

### 2.2 Detect capabilities rather than assuming them

Determine which features are actually implemented in this version. Examples include:

- Multiple screens and activities.
- View trees and layout properties.
- Logic blocks, event handlers, reporters, and nested statement stacks.
- Built-in and custom components.
- More Blocks, custom blocks, or reusable logic.
- Assets, images, fonts, strings, colors, and other resources.
- Built-in libraries and user-added dependencies.
- Custom Java/Kotlin source, native code, or external files.
- Manifest permissions and application configuration.
- Project import/export and backup/restore.
- Preview/rendering and generated-code inspection.
- Incremental build or cache mechanisms.
- Resource shrinking, code shrinking, obfuscation, or alternative compilers.

A feature name in documentation does not prove that a particular checkout supports it. Confirm the UI entry point, model representation, persistence, generator path, and build integration.

### 2.3 Establish a source map

Search for behavior, not only guessed class names. Start from visible actions and trace their call chain:

- Open or create project.
- Save project and handle autosave.
- Add, edit, duplicate, or delete a block.
- Add a view or component.
- Preview a screen.
- Import or export a project.
- Start a build and report progress.
- Generate manifest, source, resources, and layouts.
- Install, launch, or inspect the generated application.

For each important behavior, identify:

1. UI entry point.
2. Controller, service, manager, or event handler.
3. Domain/model object.
4. Persistence or serialization adapter.
5. Generator or transformation.
6. Build integration.
7. Tests and fixtures.

Prefer a short call graph with exact file paths and methods over a large unstructured file list.

### 2.4 Produce a compatibility profile for cross-version work

When porting between versions or forks, compare the source and target for:

- Data model and serialized schema.
- Block opcode/specification registry and parameter conventions.
- Component type registry and code generators.
- View/property model and XML generation.
- Built-in library/dependency registry.
- Project path and storage resolution.
- Encryption/serialization helpers.
- Archive format and import/export rules.
- Code generation templates and build tasks.
- Android and Gradle constraints.

Classify each relevant item as:

- Shared concept: the semantic behavior is equivalent.
- Equivalent implementation: a different implementation provides the same contract.
- Fork extension: exists only in one variant.
- Incompatible behavior: requires an adapter, migration, or separate implementation.
- Unknown: not yet verified.

Never port code solely because two classes have similar names.

## 3. Source-of-truth hierarchy

Use evidence in this order:

1. The exact checked-out target version: source, tests, fixtures, sample projects, and build configuration.
2. The official Sketchware Pro repository: https://github.com/Sketchware-Pro/Sketchware-Pro
3. Relevant upstream commits, tags, releases, pull requests, and documentation.
4. A well-maintained project-format or ecosystem reference, such as https://github.com/saqib-cipher/SWX_Skill, as a secondary reference only.
5. General Android/Gradle documentation for platform behavior.
6. Your own inference, clearly labeled as inference.

The official upstream README publishes a useful source-map starting point. In the documented upstream layout, these names are associated with the following responsibilities:

| Upstream reference | Documented responsibility |
| --- | --- |
| a.a.a.ProjectBuilder | Compiling an entire project |
| a.a.a.Ix | Generating AndroidManifest.xml |
| a.a.a.Jx | Generating Activity source code |
| a.a.a.Lx | Generating component source and listeners |
| a.a.a.Ox | Generating layout XML |
| a.a.a.qq | Registry of built-in library dependencies |
| a.a.a.tq | Compiling-dialog quiz behavior |
| a.a.a.yq | Organizing Sketchware project file paths |

These are **upstream navigation hints, not a universal API contract**. Names can be obfuscated, renamed, moved, split, or replaced in a fork or release. Search the target source for the responsibility and confirm the current implementation before using any listed name. The upstream README also points contributors toward the mod package for many community changes; inspect it when present, but do not assume that all forks preserve it.

## 4. Conceptual architecture

Most Sketchware-derived systems can be reasoned about as a pipeline:

Project interaction
→ structured project model
→ serialization and persistence
→ code/resource generation
→ Android build orchestration
→ APK/AAB or other output
→ installation and runtime behavior

The actual implementation may merge layers or use different class boundaries. Use this pipeline as a debugging model, not as a required architecture.

### 4.1 Editor and interaction layer

Usually contains project lists, design/editor screens, block workspaces, component/property panels, dialogs, previews, and build progress UI.

When changing UI:

- Follow the target's existing navigation and lifecycle architecture.
- Reuse its theme, dimensions, resources, toolbar patterns, and component library.
- Keep editor state consistent with the underlying project model.
- Do not make a control appear functional when it does not commit a real model change.
- Handle empty, loading, error, read-only, and recovery states.
- Avoid attaching duplicate observers/listeners during repeated navigation.
- Keep expensive parsing, filesystem operations, and builds off the main thread.
- Preserve state across configuration changes and process recreation where the existing architecture requires it.

### 4.2 Project/domain model

The project model may include:

- Project metadata and package/application settings.
- Screens or activities.
- View hierarchy and layout properties.
- Components and event bindings.
- Logic block graphs.
- Custom blocks or reusable logic definitions.
- Resources and assets.
- Library/dependency declarations.
- Custom source and native files.
- Manifest settings and permissions.
- Build options and compatibility metadata.

Identify the canonical model object for each concern. Do not add UI-only fields to serialized project data unless the format and migration strategy support it.

### 4.3 Persistence layer

Persistence may use JSON, XML, databases, binary records, encrypted files, or a mixture. Identify the actual serializers, readers, writers, codecs, schema versions, and path resolvers.

Do not write directly into persisted files when the project already provides model-level save APIs. If direct file manipulation is required, use the same serialization and encryption helpers as the target version.

### 4.4 Generation layer

Generators translate the project model into platform artifacts. Common outputs include:

- AndroidManifest.xml.
- Java or Kotlin source files.
- Layout XML.
- Drawable, values, and other resource files.
- Component implementations and event listeners.
- Dependency declarations and build metadata.

A visible editor change is incomplete until the corresponding generator and runtime behavior are handled.

### 4.5 Build layer

The build layer resolves dependencies, prepares generated files, invokes Gradle or another compiler pipeline, reports progress, handles errors, and exposes generated artifacts.

Follow the actual orchestration and task graph. Never assume the build pipeline is a single Gradle task or that every fork uses the same compiler, cache, shrinking, signing, or packaging configuration.

## 5. Project data and storage

### 5.1 Discover the storage root

Never hardcode a legacy directory such as .sketchware, an OBB path, or a fixed external-storage location.

Find the target's storage resolver and determine whether it uses:

- App-private internal storage.
- App-specific external storage.
- User-selected document/tree access.
- Legacy shared-storage paths.
- A migration layer between old and new locations.
- A version-specific project database or directory structure.

Use the platform's supported storage APIs and the project's existing abstraction. Consider Android scoped-storage restrictions and runtime permissions where relevant.

### 5.2 Treat file names as hypotheses

Project formats often use recognizable names such as project, file, logic, view, resource, library, or project_config, and may contain a files directory. These names are common clues, not a guaranteed schema.

For each target version, inspect:

- A newly created empty project.
- A project containing a screen and several views.
- A project containing event logic and nested blocks.
- A project with components and resources.
- A project with custom code or libraries, if supported.
- A project exported by the current version.

Compare the samples to discover required files, optional files, defaults, encoding, and version markers.

### 5.3 Serialization invariants

- Preserve JSON/XML types. Do not turn numbers or booleans into strings.
- Preserve unknown fields when forward compatibility requires it.
- Do not silently discard unsupported data during read-modify-write operations.
- Use stable identifiers and verify uniqueness constraints.
- Keep ordering where the format or generator relies on it.
- Apply schema migrations explicitly and make them repeatable where possible.
- Use atomic writes or the platform's transaction mechanism to reduce corruption risk.
- Avoid partially updating related project files.
- Do not expose secrets or private user content in logs.

### 5.4 Encryption and encoding

Some Sketchware-derived formats or versions may encrypt project records or files. Never assume a cipher, key, IV, mode, padding, or plaintext layout from another version.

Before reading or writing encrypted data:

1. Locate the exact target codec/cryptography implementation.
2. Trace every caller and determine which files/fields use it.
3. Check whether the key/IV or format differs by version.
4. Use the original serializer/codec rather than reimplementing it.
5. Verify a round trip with a fixture from the target version.
6. Confirm that old projects remain readable after the change.

Do not describe embedded or hardcoded application encryption keys as strong security. Encryption used for file compatibility is not a substitute for secure credential storage, integrity protection, or access control.

## 6. Logic blocks and event graphs

The block system is a structured program representation. Treat it as a typed graph or graph-like structure, not as decorative UI objects.

### 6.1 Discover the real block schema

Inspect the block model, serializer, workspace adapter, opcode/spec registry, connection validation, and code generator. Identify how the target represents:

- Unique block identity.
- Opcode or block type.
- Block category and visual specification.
- Literal and variable parameters.
- Input sockets and reporter connections.
- Next-statement connections.
- Nested statement stacks.
- Event roots and event metadata.
- Variable, list, and component references.
- Disabled, collapsed, mutation, or extra metadata.
- Custom or extension block definitions.

Names such as nextBlock, subStacks, parameters, or BlockBean may appear in some implementations, but must not be assumed. Confirm the fields from the target's actual code and fixtures.

### 6.2 Graph integrity requirements

Before saving or generating code, validate:

- Every referenced block exists.
- Every block ID satisfies the target's actual validity policy and is unique where required.
- No illegal cycles exist in next/statement chains.
- Reporter connections point to compatible input types.
- Statement connections point to valid statement sockets.
- Required inputs and parameters are present.
- Event roots are valid for their event type.
- Component, variable, and resource references resolve.
- Deleted blocks are not still referenced by another block.
- Shared references are permitted only if the target model explicitly allows them.

Do not assume ID zero is valid. Inspect the allocator and validator; use the target's allocation mechanism and uniqueness rules.

### 6.3 Code generation fidelity

- Use the existing opcode/specification registry and generator conventions.
- Preserve block semantics, evaluation order, scope, and control flow.
- Keep type conversion and null behavior consistent with the target.
- Handle missing optional inputs exactly as the existing generator expects.
- Respect event lifecycle and callback signatures.
- Escape strings and identifiers correctly.
- Avoid source fragments that break syntax or alter scope.
- Generate deterministic output where practical.

Do not replace a visual block implementation with raw source injection unless the user explicitly asks for custom code and the target supports that feature. Do not claim arbitrary Java/Kotlin can be converted into native blocks. Native blocks have schemas, connection rules, and generator semantics that generic source parsing does not reproduce reliably.

### 6.4 Block feature implementation checklist

A new or modified block may require all of the following:

1. Definition/specification and user-visible label.
2. Category, color, icon, tooltip, and workspace registration.
3. Input sockets and type constraints.
4. Serialization and deserialization.
5. Connection validation and mutation behavior.
6. Code generation.
7. Variable/component/resource lookup.
8. Preview or editor rendering support.
9. Compatibility behavior for existing projects.
10. Unit or fixture-based tests and an end-to-end build test.

Do not stop after adding the block to a palette.

## 7. Views, layouts, and preview

The visual designer usually represents a hierarchy of views, containers, and layout properties. The preview may use a different renderer or an intermediate model from the final Android layout.

Inspect how the target handles:

- View type registration and instantiation.
- Parent-child ordering.
- View IDs and ID allocation.
- Width, height, margins, padding, gravity, weight, and constraints.
- Orientation, visibility, text, colors, backgrounds, and typography.
- Layout-specific parameters.
- Nested containers and unsupported combinations.
- Custom attributes and component properties.
- Preview parsing/rendering.
- Generated XML and runtime inflation.

For every visual feature, trace the complete path:

Palette or property editor
→ view model update
→ project persistence
→ preview refresh
→ XML/layout generation
→ generated app runtime

A preview-only implementation is incomplete. A generator-only implementation can leave the editor misleading. Test both preview and generated output.

Preserve stable view IDs and references. Avoid allocating IDs in rendering code if the project model owns ID allocation. Check duplicate IDs, stale references, parent-child consistency, and layout parameter type matching.

## 8. Components, listeners, and lifecycle

Components may represent Android widgets, services, sensors, timers, media objects, or custom integrations. Their implementation can be distributed across editor metadata, the component registry, project model, generated source, and runtime support code.

For each component, establish:

- Stable component type and instance identity.
- Palette/property metadata.
- Default property values.
- Serialization format.
- Generated field declarations and initialization.
- Listener registration and removal.
- Event parameter mapping and callback generation.
- Permission and manifest requirements.
- Resource and dependency requirements.
- Activity/service lifecycle behavior.
- Cleanup behavior and error handling.

Avoid duplicate listener registration. Detach callbacks when the relevant lifecycle ends. Do not leak Activity, View, Context, or project references. Respect permission denial, unavailable hardware, and API-level differences.

A component is not complete merely because it appears in the designer. It must round-trip through persistence, generate valid source, compile, and behave correctly at runtime.

## 9. Resources and libraries

### 9.1 Resources

Determine the target's canonical resource manager and its mappings to Android resources. Common categories include images, icons, strings, colors, fonts, raw assets, drawables, and XML resources.

For resource changes:

- Use the resource manager or canonical project API.
- Validate names, extensions, dimensions, and encoding.
- Prevent duplicate or invalid resource identifiers.
- Preserve case sensitivity and path rules.
- Ensure generated references resolve.
- Handle density and configuration variants correctly.
- Remove stale generated output safely.
- Avoid editing generated copies when the source resource is elsewhere.

### 9.2 Libraries and dependencies

Find the actual dependency registry and version-resolution mechanism. Some upstream layouts have a registry associated with built-in library dependencies, but its class name may vary.

For a library change, verify:

- Maven coordinates or local artifact source.
- Version and repository availability.
- Transitive dependencies.
- Min/compile SDK requirements.
- Manifest/resource merging behavior.
- Duplicate classes and dependency conflicts.
- License and redistribution terms.
- Offline cache behavior, if supported.
- Generated project compatibility.

Do not insert arbitrary dependencies into generated Gradle files if the IDE maintains a separate dependency model.

## 10. Custom source and native code

When supported, custom Java/Kotlin or native files form another input to generated projects.

- Identify where source files are stored and how they are packaged.
- Determine whether paths, packages, and file names are validated.
- Preserve custom source through import/export and migrations.
- Understand how custom classes are included in generated source and compilation.
- Check whether code is copied, transformed, or referenced directly.
- Avoid overwriting user-authored code during regeneration.
- Keep generated and user-owned files separate.
- Never execute imported code as part of inspection.
- Treat build scripts and native build files as untrusted project input.

Do not assume all Sketchware versions support the same custom-code or native-code features.

## 11. Code generation and build pipeline

### 11.1 Trace the real build path

Start at the visible Build action and follow the complete call chain. Identify:

- Pre-build validation.
- Project snapshot or locking behavior.
- Generated-file cleanup and staging.
- Manifest generation.
- Source generation.
- Layout and resource generation.
- Dependency preparation.
- Gradle invocation or embedded compiler.
- Code/resource shrinking and obfuscation, if enabled.
- APK/AAB packaging and signing.
- Output publication and cleanup.
- Progress, cancellation, retry, and error reporting.

Use the repository's Gradle wrapper and documented build configuration. Do not upgrade Gradle, Android Gradle Plugin, Kotlin, or SDK versions as an incidental part of an unrelated feature.

### 11.2 Build correctness

A successful build means more than a zero exit code. Verify:

- Expected artifact exists and is non-empty.
- Artifact timestamp/hash corresponds to the latest build.
- Package/application identity is correct.
- Manifest and resources are present.
- No stale generated outputs were reused incorrectly.
- Build result reaches the UI correctly.
- Failure logs preserve actionable diagnostics.
- Cancellation does not leave corrupted project state.
- Incremental/cached builds do not conceal generator defects.

Never report a build as successful unless the build actually completed and the output was checked.

### 11.3 Generated-source discipline

Generated files should have a clearly defined owner. Determine which files are user-authored, IDE-authored, generated, or build intermediates.

- Do not hand-edit generated files when the source model or template is authoritative.
- Do not delete user-authored files during clean operations.
- Make generation repeatable and deterministic where practical.
- Ensure a second generation does not create duplicate declarations or listeners.
- Test regeneration after a project is reopened.

## 12. Project archive import and export

SWB is a common Sketchware project archive extension, but archive contents and conventions can vary by version or fork. Inspect real exports from the target and representative older versions.

### 12.1 Export

- Export from the canonical project representation.
- Include all required metadata, screens, logic, resources, dependencies, and custom files supported by the format.
- Preserve file names, relative paths, types, and encoding.
- Avoid including caches, credentials, build intermediates, or private app data unless the format explicitly requires them.
- Produce a deterministic archive where practical.
- Verify that the exported archive can be imported again.

### 12.2 Import

Treat every imported archive as untrusted.

- Reject absolute paths and path traversal entries.
- Normalize and validate every archive path before writing.
- Prevent symlink-based escape where the archive library exposes such entries.
- Apply limits to total expanded size, entry count, per-entry size, and compression ratio.
- Reject malformed, duplicate-conflicting, or unsupported entries safely.
- Validate metadata and project schema before replacing an existing project.
- Extract into a temporary staging directory.
- Validate the completed project before committing it to the final location.
- Use atomic move/replace where supported.
- Preserve the existing project if import fails.
- Report unsupported-version and corrupted-file errors distinctly.

Never unzip directly into an existing project directory without a rollback strategy.

### 12.3 Cross-version conversion

Use explicit version detection and migration steps. Keep migrations ordered, testable, and idempotent where possible. Preserve unknown data if the target format permits it. Do not silently downgrade a newer project to an older schema.

## 13. Android platform and UI engineering

Sketchware Pro is an Android application, so changes must respect Android lifecycle, permission, storage, threading, and API-level behavior.

- Check minSdk and actual supported API levels before using platform APIs.
- Use AndroidX/Material libraries already established by the target.
- Follow existing Java/Kotlin conventions rather than imposing a new language.
- Keep UI work on the main thread and filesystem/build work off it.
- Avoid Activity leaks and stale references.
- Respect configuration changes and process death.
- Validate dynamic colors/themes and resource qualifiers across supported versions.
- Check accessibility: labels, content descriptions, contrast, focus, touch targets, and keyboard behavior.
- Handle edge-to-edge and insets according to the project's current implementation.
- Keep permission requests contextual and handle denial gracefully.
- Use secure storage APIs for credentials and tokens; never reuse project-file encryption as credential storage.

Do not apply a visual redesign when the task is a backend defect unless the UI must change to expose correct state.

## 14. Security and trust boundaries

Assume project files may be crafted by an attacker or malformed by an earlier crash. Validate at every boundary.

Key risks include:

- Zip Slip and archive bombs.
- Path traversal and symlink escape.
- Malformed or deeply nested JSON/XML.
- Invalid block references and resource names.
- Unsafe shell/process argument construction.
- Build-script execution from imported projects.
- Secret leakage through logs, exported archives, crash reports, or generated source.
- Insecure credential storage.
- Dependency confusion or untrusted repositories.
- Deserialization gadget risks in unsafe object serialization.
- Resource exhaustion from huge images, files, or graphs.

Security rules:

1. Never execute imported scripts or source as part of project inspection.
2. Avoid shell string concatenation; use safe process APIs and validated argument arrays.
3. Validate paths after normalization and ensure the resolved destination remains inside the intended root.
4. Limit archive extraction and parser resource consumption.
5. Validate IDs, names, references, and sizes before generation.
6. Avoid logging access tokens, passwords, signing secrets, or private source contents.
7. Keep signing keys and credentials outside project archives and version control.
8. Do not disable signature, certificate, or dependency checks as a shortcut.
9. Make destructive operations explicit and recoverable.
10. Report security assumptions and remaining risks.

## 15. Testing strategy

Choose tests based on the layer changed. Do not rely on a successful compile as the only validation.

### 15.1 Discovery and baseline

Before a significant change:

- Inspect current working-tree changes.
- Run or inspect the narrowest existing baseline test.
- Record the exact build command and environment.
- Identify known pre-existing failures.
- Preserve user changes; never reset or clean the working tree blindly.

### 15.2 Model and serialization tests

Test:

- Create/read/update/save/reopen round trips.
- Old fixture compatibility.
- Missing optional fields and default values.
- Unknown-field preservation where required.
- Correct JSON/XML types.
- ID uniqueness and reference integrity.
- Encryption/codec round trips using target fixtures.
- Interrupted or failed writes.

### 15.3 Logic and generation tests

Test:

- Simple statement and reporter blocks.
- Nested control flow and event roots.
- Optional and invalid inputs.
- Variables, components, and resource references.
- Escaping and type conversions.
- Deterministic generation.
- Reopen → regenerate → compile behavior.

### 15.4 Views and components tests

Test:

- Parent-child hierarchy and ordering.
- IDs and layout parameters.
- Preview output and generated XML.
- Component defaults, properties, and event bindings.
- Listener lifecycle and cleanup.
- Permission denial and API-level variations where applicable.

### 15.5 Import/export tests

Test:

- Export then import into a clean workspace.
- Import from representative older versions.
- Corrupted and truncated archives.
- Path traversal attempts.
- Duplicate entries.
- Oversized and deeply nested content.
- Failure midway through import without damaging an existing project.

### 15.6 Build and runtime tests

At minimum for a feature that affects generated applications:

- Build a minimal project.
- Build a project exercising the changed feature.
- Reopen the project and build again.
- Inspect the actual APK/AAB artifact.
- Install and launch where an emulator/device is available.
- Confirm runtime behavior, not merely compilation.

State clearly when device testing, network access, signing, or a required SDK was unavailable.

## 16. Safe modification workflow

Use this sequence for implementation tasks.

### Phase A — Understand

1. Restate the requested behavior in testable terms.
2. Identify the exact target repository/version/fork.
3. Inspect working-tree state and relevant source.
4. Trace the current behavior end to end.
5. Find analogous implementations, tests, and fixtures.
6. Identify compatibility, storage, security, and migration risks.

### Phase B — Design

1. Define the smallest correct change.
2. List every affected layer.
3. Identify whether serialized data changes.
4. Define backward-compatible defaults or migrations.
5. Identify failure and rollback behavior.
6. Choose unit, integration, and manual validation.

### Phase C — Implement

1. Follow the target architecture and naming conventions.
2. Reuse existing model, serializer, registry, generator, and storage APIs.
3. Update all required registrations and generation paths.
4. Keep user-authored and generated files separate.
5. Add tests with fixtures representative of the target version.
6. Avoid unrelated formatting and dependency changes.

### Phase D — Verify

1. Review the diff for accidental changes.
2. Run formatting/static checks where configured.
3. Run focused tests.
4. Run the relevant Gradle task or project build.
5. Validate generated output and artifact existence.
6. Test a project round trip if persistence or archive behavior changed.
7. Review logs for warnings, regressions, and leaked information.
8. Record failures separately from pre-existing failures.

### Phase E — Report

Provide:

- Target product/fork/version/commit inspected.
- Root cause or design rationale.
- Files and layers changed.
- User-visible and data-format behavior changes.
- Compatibility and migration notes.
- Exact tests/build commands run and their results.
- Artifact path only if it was actually created and verified.
- Known limitations and unverified behavior.

## 17. Common failure patterns to avoid

- Assuming every fork has the upstream package structure.
- Using a class name from an old version without locating it in the target.
- Assuming every project file is plaintext JSON.
- Assuming all versions use the same cipher or hardcoded key.
- Hardcoding legacy shared-storage paths.
- Updating the block palette but not serialization or code generation.
- Updating preview rendering but not generated Android XML.
- Adding a component without listener lifecycle and manifest handling.
- Editing generated source instead of the model/template.
- Deleting unknown fields during project save.
- Replacing the build pipeline without understanding cancellation, cache, and cleanup behavior.
- Importing archives directly over existing projects.
- Running untrusted project scripts during inspection.
- Making a UI control decorative rather than functional.
- Adding broad dependencies or build-system upgrades unrelated to the task.
- Claiming tests or APK builds succeeded without running and inspecting them.
- Porting a fork-specific feature as though it were part of every Sketchware version.

## 18. Porting features between upstream and forks

When porting a feature:

1. Identify the source implementation and its exact commit/version.
2. Identify the target implementation and its exact commit/version.
3. Compare responsibilities and data contracts, not just file names.
4. Trace all dependencies: model, registry, UI, persistence, generator, resources, build, and tests.
5. Check whether the target already has an equivalent feature under a different name.
6. Adapt to target conventions rather than copying incompatible code wholesale.
7. Preserve unrelated target behavior.
8. Add compatibility adapters or migrations only where needed.
9. Test both newly created and pre-existing projects.
10. Document any intentionally unsupported behavior.

Never force a source-side class hierarchy onto a target that has a different architecture.

## 19. Reusable task prompt for any AI agent

Use the following instruction when delegating a Sketchware-related engineering task:

"Apply the Sketchware Pro Engineering skill. First identify the exact Sketchware/Sketchware Pro version or fork and inspect its checked-out source, configuration, tests, and project fixtures. Use the official Sketchware Pro repository as the primary upstream reference, but do not assume that upstream class names, paths, storage, serialization, encryption, or build steps are identical in this target. Trace the requested behavior from UI through the project model, persistence, generators, build, and runtime as applicable. Make the smallest complete change that preserves existing project compatibility and user data. Treat imported projects and archives as untrusted. Add or update tests, run the relevant checks, inspect generated output and artifacts, and report exact evidence. Do not invent files, APIs, test results, or build success. Do not make unrelated changes."

## 20. Reference links

- Official Sketchware Pro source and contribution guide: https://github.com/Sketchware-Pro/Sketchware-Pro
- Official upstream README/source map: https://github.com/Sketchware-Pro/Sketchware-Pro/blob/main/README.md
- Secondary Sketchware project-system reference: https://github.com/saqib-cipher/SWX_Skill
- Android developer documentation: https://developer.android.com/
- Gradle documentation: https://docs.gradle.org/

Always re-check links, current source, releases, and repository policies when a task depends on current information.

## 21. Maintenance policy

Update this skill when verified upstream architecture or widely used project formats change. Keep universal guidance separate from fork-specific notes. Any fork-specific note must identify the repository/version/commit it applies to and must never be presented as a universal Sketchware rule.

When information is uncertain, write "verify in target source" rather than turning an assumption into a rule. The skill should make an agent more reliable across versions—not encourage it to pretend those versions are identical.
