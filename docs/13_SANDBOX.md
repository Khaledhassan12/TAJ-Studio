# 13_SANDBOX.md

## Overview
The Sandbox is a critical security and functional component of Agora, providing a virtualized, isolated environment for the AI model to execute code (Python, Node.js, Shell) without risking the host Android system. It is implemented using **PRoot** to create a user-space `chroot` into an **Alpine Linux** filesystem.

## Core Components

### 1. `ProotSandboxManager.kt`
- **Role**: Manages the lifecycle of the virtual environment.
- **Responsibilities**:
    - **Installation**: Downloads and extracts the Alpine Linux `minirootfs` (approx. 3MB compressed) into the app's internal storage.
    - **Binary Management**: Extracts the `proot` and `talloc` native libraries required for virtualization.
    - **Execution**: Orchestrates command execution via `ProcessBuilder`, setting up the necessary environment variables (`LD_LIBRARY_PATH`, `PROOT_LOADER`, `PROOT_TMP_DIR`).
    - **Package Management**: Implements a custom, network-aware `apk` (Alpine Package Keeper) wrapper. Since `proot` struggles with Android's network stack in some configurations (like VPNs), the manager handles package downloads via Android's native HTTP client before installing them inside the sandbox.

### 2. `SandboxDocumentsProvider.kt`
- **Role**: Exposes the sandbox internal filesystem to the Android OS.
- **Framework**: **Storage Access Framework (SAF)**.
- **Utility**: Allows the user to use their favorite Android file manager to browse, edit, or copy files from the AI's sandbox environment.
- **Root ID**: Defaults to `/home/agora` (mapped internally to `/root` in the sandbox) to provide a standard user experience.

### 3. Native Stub (`proot_jni.cpp`)
- **Role**: Triggers the extraction of native libraries from the APK.
- **Detail**: While execution happens via `ProcessBuilder`, the `System.loadLibrary("agora_proot")` call ensures that the Android system extracts the `.so` files to a location where they can be executed.

## Package Management Flow
1. Model calls `execute_shell_command("apk add python3")`.
2. `ProotSandboxManager` intercepts and downloads the `.apk` and its dependencies on the Android side.
3. The files are moved into the sandbox's `/tmp`.
4. `proot` is used to run `apk add --no-network` on the local files.

## Filesystem Mapping
- **Physical**: `context.filesDir/alpine-rootfs/`
- **Virtual**: `/` (inside the sandbox)
- **Home Dir**: `context.filesDir/sandbox-home/` is bind-mounted to `/home/agora`. This ensures user data persists even if the rootfs is reset.

## Execution Architecture
```mermaid
graph TD
    App[Agora App] -- ProcessBuilder --> PB[proot_exec.so]
    PB -- chroot --> RF[Alpine Rootfs]
    PB -- bind --> DEV[/dev, /proc, /sys]
    PB -- mount --> HOME[Sandbox Home]
    
    subgraph Sandbox
        CMD[Shell/Python/Compiler]
    end
    
    RF --> CMD
    HOME --> CMD
```

## Security Model
- **Isolation**: Commands cannot see the Android filesystem outside of the `alpine-rootfs` and the specific bind-mounts.
- **Privileges**: Runs as a non-root user (internally mapped to the app's UID), so it cannot perform system-level Android actions.
- **Network**: Uses Android's network stack, which can be restricted via standard app permissions.

## Possible Improvements
- **Resource Limiting**: Adding `cgroups` or `rlimit` support to prevent "fork bombs" or memory exhaustion within the sandbox.
- **Snapshotting**: Allowing the user to "save" the sandbox state and revert if the AI breaks the environment.
