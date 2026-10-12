#!/usr/bin/env python3
"""Safely prepare one Android patch release per new main branch push (stdlib only).

A bot-created version commit does not start another GitHub Actions run. The
current workflow carries that commit SHA into the existing signed release job.
"""
import os
from pathlib import Path
import re
import subprocess
import sys

from release_ci import GRADLE, GitHub, ReleaseError, parse_version, version_tuple, git, TAG

MAX_CODE = 2100000000


def choose_version(current_name, current_code, latest_name, latest_code, at_release_commit=False):
    """Return (new_name, new_code, needs_bump).

    Explicitly advanced versions are preserved. Both version fields must
    advance together. Never downgrade or overwrite an existing release.
    """
    current = version_tuple(current_name)
    latest = version_tuple(latest_name)
    if current == latest and current_code == latest_code:
        if at_release_commit:
            return current_name, current_code, False
        if current_code >= MAX_CODE or current[2] >= 2147483647:
            raise ReleaseError("Patch or Android versionCode limit reached; manual major/minor release required.")
        return f"{current[0]}.{current[1]}.{current[2] + 1}", current_code + 1, True
    if current > latest and current_code > latest_code:
        return current_name, current_code, False
    raise ReleaseError(
        f"Current {current_name}/{current_code} must either equal the latest "
        f"published {latest_name}/{latest_code}, or increase BOTH fields."
    )


def rewrite_gradle(source, name, code):
    # parse_version enforces unique, valid literal assignments up front.
    parse_version(source)
    name_pattern = r'(?m)^([ \t]*versionName[ \t]*=[ \t]*)"([0-9]+\.[0-9]+\.[0-9]+)"([ \t]*)$'
    code_pattern = r'(?m)^([ \t]*versionCode[ \t]*=[ \t]*)([0-9]+)([ \t]*)$'
    result, n = re.subn(name_pattern, lambda m: m.group(1) + '"' + name + '"' + m.group(3), source)
    if n != 1:
        raise ReleaseError("Could not uniquely rewrite versionName.")
    result, n = re.subn(code_pattern, lambda m: m.group(1) + str(code) + m.group(3), result)
    if n != 1 or parse_version(result) != (name, code):
        raise ReleaseError("Could not safely rewrite versionCode.")
    return result


def check_remote_head(event_sha):
    # Rerunning an interrupted workflow may find its own bot-generated child
    # commit already on main. Only reuse that exact one-file bump commit.
    remote = git("rev-parse", "refs/remotes/origin/main")
    if remote == event_sha:
        return event_sha
    parent = git("rev-parse", "refs/remotes/origin/main^")
    subject = git("log", "-1", "--format=%s", remote)
    files = git("diff", "--name-only", event_sha, remote).splitlines()
    if parent == event_sha and subject.startswith("chore(release): bump to v") and files == [GRADLE]:
        git("checkout", "--detach", remote)
        return remote
    raise ReleaseError(
        "main advanced beyond this event; refusing to overwrite newer work. "
        "The newer push should run its own release workflow."
    )


def main():
    event_sha = os.environ.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[0-9a-f]{40}", event_sha):
        raise ReleaseError("Expected a full GITHUB_SHA from the main push.")
    if git("rev-parse", "HEAD") != event_sha:
        raise ReleaseError("Workflow checkout does not match triggering commit.")
    if os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ReleaseError("Version preparation only accepts main branch pushes.")

    head = check_remote_head(event_sha)
    api = GitHub(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
    release = api.request("GET", "/releases/latest")
    tag = release.get("tag_name", "")
    if not TAG.fullmatch(tag) or release.get("draft") or release.get("prerelease"):
        raise ReleaseError("Latest stable release is missing or has an invalid tag.")
    published_name, published_code = parse_version(git("show", f"{tag}:{GRADLE}"))
    if tag != "v" + published_name:
        raise ReleaseError("Latest release tag does not match the tagged Gradle version.")
    published_sha = git("rev-parse", f"{tag}^{{commit}}")
    result = subprocess.run(["git", "merge-base", "--is-ancestor", published_sha, head], check=False)
    if result.returncode:
        raise ReleaseError("Latest published release is not an ancestor of main; manual review needed.")

    path = Path(GRADLE)
    source = path.read_text(encoding="utf-8")
    current_name, current_code = parse_version(source)
    name, code, changed = choose_version(
        current_name, current_code, published_name, published_code, head == published_sha
    )
    if changed:
        path.write_text(rewrite_gradle(source, name, code), encoding="utf-8")
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            handle.write(f"changed={str(changed).lower()}\n")
            handle.write(f"version={name}\n")
            handle.write(f"version_code={code}\n")
    print(f"Prepared v{name} (versionCode {code}); needs_version_commit={changed}")


if __name__ == "__main__":
    try:
        main()
    except (ReleaseError, KeyError, ValueError, OSError) as exc:
        print(f"::error::{exc}", file=sys.stderr)
        sys.exit(1)
