# 12_MCP_ANALYSIS.md

## Overview
The **Model Context Protocol (MCP)** is an open standard designed to enable AI models to seamlessly access external data and tools. Agora has dual-level involvement with MCP: as a **Host** (connecting to external servers) and potentially as a **Remote Tool Surface** (via the Conch protocol).

## MCP in the Web UI
The project bundles a sophisticated web interface (from `llama.cpp`) which contains a complete MCP implementation in TypeScript.
- **Client implementation**: `tools/server/webui/src/lib/services/mcp.service.ts`.
- **Capabilities**:
    - **Resource Browsing**: Browse files and data sources exposed by MCP servers.
    - **Prompt Templates**: Load standardized prompt templates from servers.
    - **Dynamic Tools**: Dynamically add new tools to the model's repertoire without UI changes.
- **Transport**: Supports both **SSE** and **stdio** transports for communicating with MCP servers.

## Conch as an MCP Gateway
The README mentions "Conch as a Claude Desktop MCP server." This indicates that the custom **Conch** protocol is designed to be a bridge.
- **Scenario**: A user runs Claude Desktop on their Mac/PC.
- **Integration**: Claude connects to a "Conch MCP Server."
- **Execution**: The server uses the Conch protocol to securely "reach into" the Android device running Agora.
- **Action**: Claude can then call tools *on the Android phone* (e.g., "Take a photo," "Run a command in the Android Sandbox," "Search local Android memories").

## Architectural Integration
```mermaid
graph TD
    subgraph MobileDevice [Android Phone (Agora)]
        AS[Agora Sandbox]
        AM[Local Memory]
        CP[Conch Protocol Handler]
    end
    
    subgraph Desktop [Computer]
        CD[Claude Desktop / AI Agent]
        CMS[Conch MCP Server]
        
        CD -- calls tool --> CMS
        CMS -- secure tunnel --> CP
        CP -- executes --> AS
        CP -- reads --> AM
    end
```

## Security & Encryption
- **Authentication**: Conch uses ECDH key exchange to establish a shared secret.
- **Integrity**: All commands and data are signed with HMAC-SHA256.
- **Privacy**: The Android user must explicitly authorize remote access to their shell or filesystem.

## Current Limitations
- **Android Host**: The native Android UI (Compose) does not yet have a full native Kotlin MCP client; it currently relies on hardcoded `ToolProvider` implementations.
- **Discovery**: Connecting to a new MCP server from the Android app requires manual URL entry in settings.

## Possible Improvements
- **Native Kotlin MCP Client**: Implementing a pure Kotlin MCP client within the `:app` module to allow the Android app to connect to any MCP server (like Brave Search or Google Drive).
- **Auto-Discovery**: Support for discovering local network MCP servers via mDNS (Bonjour).
