---
name: sketchware-pro-engineering
description: Universal, repository-agnostic AI engineering skill for the original Sketchware Pro source and any Sketchware Pro version or modified fork. Covers project architecture, visual blocks, events, views, components, resources, persistence, encryption, SWB import/export, code generation, build pipelines, testing, security, and cross-version compatibility.
---

# Sketchware Pro Engineering Skill

Use this skill for tasks involving Sketchware Pro source code, modified Sketchware Pro versions, Sketchware-derived forks, or Sketchware project files and backups.

This skill is designed to work with any AI coding agent. It is not tied to a user, repository, branch, fork, device, package name, Android SDK, or AI vendor.

## Mandatory first steps

1. Read the companion `Sketchware Source Map.md` for the detailed architecture, project-system concepts, compatibility rules, safety practices, and modification playbooks.
2. Identify the actual repository or project being modified. Record its remote, commit, branch, version markers, and working-tree state when available.
3. Use the official [Sketchware Pro source repository](https://github.com/Sketchware-Pro/Sketchware-Pro) as the primary upstream reference.
4. Inspect the target checkout before relying on any class name, method, path, schema, encryption boundary, opcode, numeric ID, or build command.
5. Read local developer/agent instructions and the actual build configuration before making changes.

## Core compatibility principle

Sketchware versions share a core programming model: projects, screens, visual views, events, variables, lists, native logic blocks, components, resources, code generation, and Android builds.

However, this does **not** mean their internal implementations are identical. Package names, class names, project schemas, encryption, storage paths, block/opcode registries, component IDs, resource handling, dependencies, APIs, and build stages can differ.

Treat the target checkout as authoritative for the exact behavior being changed. Compare it with official upstream when useful, but never transplant assumptions from another fork without verification.

## Required engineering behavior

- Preserve native editable visual-block logic. Do not replace it with raw Java/source-text blocks as a shortcut.
- Preserve project compatibility and unknown data where safe.
- Trace changes through the complete lifecycle: editor/UI → model → persistence → generator → build → runtime.
- Use the target serializer and crypto implementation; never guess encryption formats.
- Validate block IDs, references, nesting, types, and opcode parameters.
- Validate view trees, component registrations, resource references, and generated XML.
- Treat archives and imported project files as untrusted input.
- Prevent path traversal, partial destructive restores, and accidental project-data loss.
- Keep credentials and signing material out of source control.
- Make the smallest coherent change and avoid unrelated refactors.
- Add regression tests or fixtures for project-model and generator changes.
- Run supported tests/build tasks and report exact results.
- Never claim a test, build, APK, installation, or artifact was verified unless it actually was.

## Universal workflow

1. Discover the target source tree and version.
2. Read repository instructions, build configuration, and relevant tests.
3. Find the actual models, serializers, registries, editors, and generators.
4. Trace callers, data flow, and generated outputs.
5. Define compatibility risks and regression cases.
6. Implement the smallest safe change using existing abstractions.
7. Validate persistence and load/reopen behavior where project data is involved.
8. Validate generated source/resources and run relevant build/runtime checks.
9. Inspect the complete diff for accidental changes, secrets, and data loss.
10. Report changed files, exact commands, evidence, and remaining limitations.

## Reference precedence

1. Target checkout implementation, tests, and project fixtures.
2. Target checkout generated artifacts and observed runtime behavior.
3. Official Sketchware Pro source: https://github.com/Sketchware-Pro/Sketchware-Pro
4. Supplementary project-format reference: https://github.com/saqib-cipher/SWX_Skill
5. Companion `Sketchware Source Map.md`.

If this skill or an external reference conflicts with the target implementation, verify the target's actual behavior instead of forcing the source to match the reference.

## Portable use

Keep `SKILL.md` and `Sketchware Source Map.md` together in a skill directory, or provide both files to the chosen AI agent. Agents that support the Agent Skills convention can load the directory as a skill. Other agents can load `SKILL.md` from project instructions or custom instructions and follow the companion source map.

Suggested instruction for any AI agent:

> For any Sketchware Pro or Sketchware-derived task, load the sketchware-pro-engineering skill first. Use the official Sketchware Pro repository as the primary upstream reference, inspect the actual target checkout, follow the companion source map, preserve native project and block compatibility, and verify changes using tests, builds, and runtime evidence.
