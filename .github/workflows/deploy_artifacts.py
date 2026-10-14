import asyncio
import os
import subprocess
from pathlib import Path

from telethon import TelegramClient


def required_env(name):
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(
            f"Missing required GitHub Actions secret/environment variable: {name}"
        )
    return value


def get_git_commit_info():
    def git_value(fmt):
        return subprocess.check_output(
            ["git", "log", "-1", f"--pretty=format:{fmt}"],
            text=True,
        ).strip()

    return (
        git_value("%an"),
        git_value("%s"),
        git_value("%H"),
        git_value("%h"),
    )


def human_readable_size(size, decimal_places=2):
    for unit in ["B", "KB", "MB", "GB", "TB"]:
        if size < 1024.0:
            break
        size /= 1024.0
    return f"{size:.{decimal_places}f} {unit}"


async def progress(current, total):
    percentage = (current / total) * 100 if total else 100
    print(
        f"{percentage:.2f}% uploaded - "
        f"{human_readable_size(current)}/{human_readable_size(total)}",
        end="\r",
        flush=True,
    )


async def main():
    api_id = int(required_env("API_ID"))
    api_hash = required_env("API_HASH")
    bot_token = required_env("BOT_TOKEN")
    chat_id = required_env("CHAT_ID")
    topic_id = os.getenv("TOPIC_ID", "").strip()
    apk_path = Path(required_env("APK_PATH"))

    if not apk_path.is_file():
        raise FileNotFoundError(f"APK file not found: {apk_path}")

    commit_author, commit_message, commit_hash, short_hash = get_git_commit_info()
    caption = (
        f"**TAJ Studio APK**\n"
        f"**Commit by:** {commit_author}\n"
        f"**Commit message:** {commit_message}\n"
        f"**Commit:** #{short_hash}\n"
        f"**Android:** 8.0+"
    )

    # Use a fresh session for this one-off CI job; never cache Telegram sessions.
    session_path = Path("bot_session")
    client = TelegramClient(str(session_path), api_id, api_hash)

    try:
        await client.start(bot_token=bot_token)
        send_args = {
            "entity": chat_id,
            "file": str(apk_path),
            "caption": caption,
            "parse_mode": "markdown",
            "progress_callback": progress,
        }
        if topic_id:
            send_args["reply_to"] = int(topic_id)

        print(f"Sending {apk_path.name} ({human_readable_size(apk_path.stat().st_size)}) to Telegram chat {chat_id}")
        await client.send_file(**send_args)
        print("\nTelegram upload completed successfully.")
    finally:
        if client.is_connected():
            await client.disconnect()
        for suffix in ("", "-journal", "-wal", "-shm"):
            try:
                Path(f"{session_path}{suffix}").unlink(missing_ok=True)
            except OSError:
                pass


if __name__ == "__main__":
    asyncio.run(main())
