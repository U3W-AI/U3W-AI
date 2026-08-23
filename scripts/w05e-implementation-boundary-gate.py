#!/usr/bin/env python3
from __future__ import annotations

import fnmatch
import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
CONTRACT = ROOT / ".fbs-engineering" / "w05e-authoritative-readback-contract.json"


def normalize(value: str) -> str:
    return value.replace("\\", "/").removeprefix("./")


def allowed(path: str, patterns: list[str]) -> bool:
    value = normalize(path)
    for raw in patterns:
        pattern = normalize(raw)
        if fnmatch.fnmatchcase(value, pattern):
            return True
        if pattern.endswith("/**") and value.startswith(
            pattern[:-3].rstrip("/") + "/"
        ):
            return True
    return False


def git(*arguments: str) -> bytes:
    result = subprocess.run(
        ["git", "-C", str(ROOT), *arguments],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout


def changed_paths() -> list[str]:
    payload = git("status", "--porcelain=v1", "-z", "--untracked-files=all")
    paths: list[str] = []
    entries = payload.decode("utf-8", errors="strict").split("\0")
    index = 0
    while index < len(entries):
        entry = entries[index]
        index += 1
        if not entry:
            continue
        status = entry[:2]
        path = entry[3:]
        if "R" in status or "C" in status:
            if index >= len(entries) or not entries[index]:
                raise RuntimeError("rename_or_copy_status_missing_source")
            index += 1
        paths.append(normalize(path))
    return sorted(set(paths))


def main() -> int:
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    boundary = contract["contracts"]["writableBoundary"]
    patterns = boundary["allowedAtContractRevision"]
    paths = changed_paths()
    outside = [path for path in paths if not allowed(path, patterns)]
    forbidden = [
        path for path in paths
        if path.lower().endswith(".sql")
        or "connector" in path.lower()
        or "listed-runtime" in path.lower()
    ]
    diff_check = subprocess.run(
        ["git", "-C", str(ROOT), "-c", "core.whitespace=cr-at-eol",
         "diff", "--check"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    valid_stage = contract.get("stage") == "implementation"
    release_disabled = (
        contract.get("policy", {}).get("productionReleaseAllowedAtThisRevision")
        is False
    )
    ok = (
        not outside and not forbidden and diff_check.returncode == 0
        and valid_stage and release_disabled
    )
    report = {
        "schemaVersion": "fbsir.w05eImplementationBoundaryGate.v1",
        "status": "PASS" if ok else "FAIL",
        "sourceHead": git("rev-parse", "HEAD").decode().strip(),
        "stage": contract.get("stage"),
        "dirtyImplementation": bool(paths),
        "changedPathCount": len(paths),
        "changedPaths": paths,
        "outsideWritableBoundary": outside,
        "forbiddenChangedPaths": forbidden,
        "diffCheckPassed": diff_check.returncode == 0,
        "productionReleaseAllowedAtThisRevision": not release_disabled,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
