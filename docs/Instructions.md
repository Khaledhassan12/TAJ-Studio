# TAJ EGYPT PROJECT INSTRUCTIONS

## Project Identity

This repository is NOT a chat application.

This repository is being transformed into TAJ EGYPT, a production-grade Android AI Agent Platform.

The core philosophy is:

"Everything is an Agent."

Never revert to a chat-first architecture.

Never redesign completed systems.

Always continue evolving the existing architecture.

---

## General Rules

Always analyze the current source code before making changes.

Always detect completed work automatically.

Never regenerate completed modules.

Never replace production-ready implementations with simplified versions.

Never redesign an already completed architecture.

Extend existing systems instead.

Maintain backward compatibility at all times.

Keep the project compiling after every modification.

Build frequently.

Fix compilation errors immediately.

Modify only real project files.

Never create fake implementations.

Never create placeholder code unless absolutely required.

---

## Documentation Is The Source Of Truth

Always keep these files updated:

docs/Everything.md
docs/ROADMAP.md
docs/ROADMAP_V2.md

Read them before implementing anything.

If implementation changes, update the documentation immediately.

Everything.md must always describe the real current state of the project.

ROADMAP files must always reflect actual implementation progress.

---

## Existing Architecture

The following systems already exist.

Do NOT redesign them.

Do NOT replace them.

Continue extending them only.

- Agent Engine
- Tool Engine
- Memory Engine
- Workspace Engine
- Dashboard
- Navigation
- Agent UI
- GenerationManager integration
- Room database migrations

Always continue from the existing implementation.

---

## Modular Architecture

Prefer dedicated modules.

Examples:

:app

:core:agent

:core:tool

:core:memory

:core:workspace

:core:mcp

:core:plugin

:core:model

:core:provider

:core:automation

:core:voice

:core:vision

:core:code

:core:security

Avoid circular dependencies.

Prefer interfaces.

Keep modules independent.

---

## AI Provider Policy

Maintain production-grade support for:

OpenAI

Google Gemini

Anthropic

OpenRouter

DeepSeek

Ollama

LM Studio

AIHubMix

NVIDIA NIM

Local GGUF

llama.cpp

vLLM

Text Generation WebUI

KoboldCpp

Jan AI

LiteLLM

Azure OpenAI

Groq

Together AI

Fireworks AI

Cerebras

Cohere

Mistral

HuggingFace

Perplexity

xAI

Vertex AI

AWS Bedrock

Cloudflare Workers AI

SambaNova

Replicate

Never remove providers.

Only expand support.

---

## Android Platform Philosophy

TAJ EGYPT is intended to become a complete Android AI platform inspired by ideas from:

ChatGPT

Claude

Gemini

Perplexity

Grok

Cursor

Claude Code

OpenManus

Hermes

MobileClaw

Google AI Edge

MCP Hosts

while remaining a native Android application.

---

## UI Philosophy

Material 3.

Modern Android.

Drawer navigation.

Bottom Sheets.

Dashboard-first.

Agent-first.

Chat is only one feature inside the platform.

Never return to Chat-first UX.

---

## Agent Philosophy

Everything should eventually become an Agent.

Examples:

Memory Agent

Workspace Agent

Tool Agent

Search Agent

Reasoning Agent

Planning Agent

Learning Agent

Voice Agent

Vision Agent

Automation Agent

Plugin Agent

MCP Agent

Provider Agent

Scheduler Agent

Code Agent

---

## Database Rules

Never delete user data.

Never break existing databases.

Always create Room migrations.

Always preserve backward compatibility.

---

## Code Quality

Prefer Kotlin.

Prefer immutable models.

Prefer interfaces.

Prefer dependency injection.

Avoid giant classes.

Avoid duplicated logic.

Split responsibilities.

Optimize performance continuously.

---

## Performance

Continuously optimize:

CPU

Memory

Battery

GPU

Rendering

Startup

Background execution

Tool execution

Token usage

Context usage

---

## Documentation Policy

After every major implementation:

Update Everything.md

Update ROADMAP.md

Update ROADMAP_V2.md

Document every completed subsystem.

Document architecture changes.

Document database migrations.

Document new modules.

Never leave documentation outdated.

---

## Development Workflow

Always:

1. Analyze current implementation.
2. Detect unfinished work.
3. Continue from the first unfinished task.
4. Keep the project compiling.
5. Update documentation.
6. Commit logical progress internally.
7. Continue automatically.

Never stop because a roadmap phase finished.

Roadmap completion does NOT mean project completion.

Automatically continue with the next unfinished roadmap item.

---

## Final Goal

The goal is NOT to build a chatbot.

The goal is to build TAJ EGYPT:

A modular, scalable, production-grade Android AI Agent Operating System capable of continuous evolution without requiring architectural rewrites.

Every new implementation must move the project closer to this objective.