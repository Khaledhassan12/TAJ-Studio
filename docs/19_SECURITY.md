# 19_SECURITY.md

## Overview
Agora is designed for privacy-conscious AI power users. Its security model centers on on-device data sovereignty, strong encryption for sensitive credentials, and an isolated virtualization layer for untrusted code execution.

## Data Sovereignty (The "No Cloud" Rule)
- **Local Persistence**: Conversations, memories, and settings are stored exclusively in the app's internal database (Room).
- **No Telemetry**: The app does not send usage stats, crash logs (unless opted-in), or conversation data to any central server owned by the developers.
- **Direct API**: Communication happens directly between the user's device and the AI provider (OpenAI, Google, etc.).

## Cryptography

### 1. Credentials at Rest (`SecretCrypto.kt`)
- **Algorithm**: **AES-256-GCM**.
- **Key Management**: Encryption keys are generated and stored in the **Android Keystore System**. This ensures keys are hardware-backed (where available) and non-exportable.
- **Envelope Format**: Encrypted values are stored as `enc:v1:base64(iv + ciphertext + tag)` within the DataStore preferences.
- **Scope**: Protects API keys, SSH passwords, and remote server credentials.

### 2. Remote Communication (`ShellCrypto.kt`)
- **Protocol**: Custom **Conch** protocol.
- **Key Exchange**: Uses **ECDH (X25519)** to establish a shared secret between the phone and the remote server.
- **Encryption**: Commands and data are encrypted with **AES-256-GCM**.
- **Integrity**: Every request is signed with **HMAC-SHA256**.
- **Trust Model**: Implements **TOFU (Trust-On-First-Use)** for SSH host keys, preventing man-in-the-middle attacks.

## Environment Isolation (Sandbox)
Untrusted AI-generated code is never executed directly on the Android OS.
- **PRoot**: Creates a user-space isolation layer using `chroot`.
- **Privileges**: The sandbox process runs with the same low-level UID as the Android app, meaning it cannot access system files, other apps' data, or hardware it hasn't been granted permission for.
- **Path Sanitization**: Both the `MemoryManager` and `SandboxManager` verify all paths using `File.canonicalPath` to block `../` path traversal attacks.

## Network Security (`HttpClient.kt`)
- **Cleartext Guard**: The `HttpClient` includes a safety check that blocks the transmission of credentials (like `Authorization` headers) over plain `http://` URLs, except for verified local network addresses (Ollama, local proxies).
- **Certificate Handling**: Relies on the Android system's CA store for verifying TLS certificates of AI providers.

## Permission Model (Manifest)
Agora requests minimal permissions, following the principle of least privilege:
- `INTERNET`: Essential for cloud AI and remote shell.
- `FOREGROUND_SERVICE_DATA_SYNC`: Required to prevent the system from killing long-running AI inference tasks.
- `POST_NOTIFICATIONS`: For background agent status updates and completion alerts.
- `MANAGE_DOCUMENTS`: Scoped permission for the `SandboxDocumentsProvider`.
- `SCHEDULE_EXACT_ALARM`: Required for precise cron-based tasks.

## Threat Model & Mitigations
| Threat | Mitigation |
| :--- | :--- |
| **Credential Theft** | Android Keystore-backed AES-256-GCM encryption. |
| **Malicious Code Exec** | Alpine Linux Sandbox via PRoot. |
| **Data Leakage** | Local-first storage; no intermediary proxy servers. |
| **Man-in-the-Middle** | TLS 1.3 enforced; TOFU for SSH/Conch. |
| **Path Traversal** | Canonical path validation on all file tools. |

## Future Improvements
- **Biometric Lock**: Adding an option to gate access to the app or specific "private" conversations with a fingerprint or face scan.
- **Process Memory Protection**: Hardening the JNI layer to prevent sensitive strings from lingering in native memory after inference ends.
吐
吐
