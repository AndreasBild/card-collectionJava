#!/usr/bin/env python3
"""
Antigravity PreToolUse Lifecycle Guard for card-collectionJava.
Enforces:
1. Bounded reads on massive dataset files (cards.json, market-data-cache.json, MissingImages.txt).
2. Working tree hygiene (blocks `git add .` / `git add -A`).
3. Branch protection (blocks `git commit` directly on `main`).
"""
import json
import os
import re
import subprocess
import sys


def allow() -> None:
    print(json.dumps({"decision": "allow"}))
    sys.exit(0)


def deny(reason: str) -> None:
    print(json.dumps({"decision": "deny", "reason": reason}))
    sys.exit(0)


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except Exception:
        allow()

    tool_call = payload.get("toolCall") or {}
    tool_name = tool_call.get("name", "")
    args = tool_call.get("args") or {}
    cwd = payload.get("cwd") or os.getcwd()

    if tool_name == "read_file":
        path = str(args.get("Path", ""))
        guarded_suffixes = (
            "content/json/cards.json",
            "content/json/market-data-cache.json",
            "MissingImages.txt",
        )
        if any(path.endswith(suffix) for suffix in guarded_suffixes):
            start = args.get("StartLine")
            end = args.get("EndLine")
            if start is None or end is None:
                deny(
                    "Massive File Invariant: Do not read cards.json, market-data-cache.json, "
                    "or MissingImages.txt in full. Specify StartLine and EndLine (<= 150 lines) "
                    "or use grep via run_command."
                )
            try:
                if int(end) - int(start) > 150:
                    deny(
                        "Massive File Invariant: Line slice exceeds 150 lines. "
                        "Request a narrower StartLine/EndLine range (<= 100 lines recommended)."
                    )
            except (TypeError, ValueError):
                pass

    elif tool_name == "run_command":
        cmd = str(args.get("CommandLine", ""))
        if re.search(r"(?:^|[;&|]\s*)git\s+add\s+(?:\.|-A|--all)(?:\s|$|[;&|])", cmd):
            deny(
                "Working Tree Hygiene Invariant: Never run `git add .` or `git add -A`. "
                "Stage only task-specific files to protect dirty datasets (cards.json, MissingImages.txt)."
            )
        if re.search(r"(?:^|[;&|]\s*)git\s+commit\b", cmd):
            try:
                branch = subprocess.check_output(
                    ["git", "rev-parse", "--abbrev-ref", "HEAD"],
                    cwd=cwd,
                    stderr=subprocess.DEVNULL,
                    text=True,
                    timeout=2,
                ).strip()
                if branch == "main":
                    deny(
                        "Branch Protection Invariant: Direct commits on `main` are forbidden. "
                        "Checkout an isolated feat/*, fix/*, or chore/* branch first."
                    )
            except Exception:
                pass

    allow()


if __name__ == "__main__":
    main()
