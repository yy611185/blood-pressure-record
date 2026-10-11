#!/usr/bin/env python3
"""Fail-closed release detection and resumable GitHub publication (stdlib only)."""

import argparse
import base64
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request


VERSION = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
TAG = re.compile(rf"v({VERSION})\Z")
CERTIFICATE = "46dfcbf4da054df8a8aca30e0df4df323161cbe4363a9eac7683a9da87d44012"
GRADLE = "app/build.gradle.kts"


class ReleaseError(RuntimeError):
    pass


class SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        destination = urllib.parse.urlparse(new_url)
        hostname = destination.hostname or ""
        if destination.scheme != "https" or not (
            hostname in ("api.github.com", "uploads.github.com") or hostname.endswith(".githubusercontent.com")
        ):
            raise ReleaseError("Refusing an untrusted GitHub asset redirect.")
        redirected = super().redirect_request(request, response, code, message, headers, new_url)
        if redirected and urllib.parse.urlparse(request.full_url).hostname != hostname:
            # Asset downloads redirect to a signed CDN URL. The repository token
            # is only for the API and must never accompany that redirect.
            for name in list(redirected.headers):
                if name.lower() == "authorization":
                    del redirected.headers[name]
            for name in list(redirected.unredirected_hdrs):
                if name.lower() == "authorization":
                    del redirected.unredirected_hdrs[name]
        return redirected


def parse_version(source):
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    source = re.sub(r"//[^\n]*", "", source)
    values = {}
    for key in ("versionName", "versionCode"):
        matches = re.findall(rf"^\s*{key}\s*=\s*(.*?)\s*$", source, re.M)
        if len(matches) != 1:
            raise ReleaseError(f"Expected exactly one literal {key} assignment in {GRADLE}.")
        values[key] = matches[0]
    name = re.fullmatch(rf'"({VERSION})"', values["versionName"])
    code = re.fullmatch(r"[1-9][0-9]*", values["versionCode"])
    if not name or not code or int(code[0]) > 2100000000:
        raise ReleaseError("versionName must be X.Y.Z without leading zeros; versionCode must be 1..2100000000.")
    return name[1], int(code[0])


def version_tuple(name):
    return tuple(map(int, name.split(".")))


def git(*args):
    result = subprocess.run(["git", *args], capture_output=True, text=True, check=False)
    if result.returncode:
        raise ReleaseError(f"git {' '.join(args[:2])} failed: {result.stderr.strip()}")
    return result.stdout.strip()


class GitHub:
    def __init__(self, repository, token):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
            raise ReleaseError("GITHUB_REPOSITORY must be owner/repository.")
        if not token:
            raise ReleaseError("GITHUB_TOKEN is required.")
        self.repository = repository
        self.token = token
        self.base = f"https://api.github.com/repos/{repository}"
        self.opener = urllib.request.build_opener(SafeRedirectHandler())

    def request(self, method, path, body=None, *, binary=False, content_type=None):
        url = path if path.startswith("https://") else self.base + path
        # Asset upload and API URLs must remain on GitHub, including server responses.
        if urllib.parse.urlparse(url).hostname not in ("api.github.com", "uploads.github.com"):
            raise ReleaseError("Refusing a non-GitHub API URL.")
        data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/octet-stream" if binary else "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "blood-pressure-record-release-ci",
        }
        if data is not None:
            headers["Content-Type"] = content_type or "application/json"
        try:
            with self.opener.open(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=120) as response:
                raw = response.read()
        except urllib.error.HTTPError as exc:
            # Only a genuine 404 represents absence. Authentication, rate limits and
            # transient failures must never be mistaken for a missing release/tag.
            raise ReleaseError(f"GitHub {method} {urllib.parse.urlparse(url).path} returned HTTP {exc.code}.") from exc
        except (urllib.error.URLError, TimeoutError, http.client.HTTPException) as exc:
            raise ReleaseError(f"GitHub {method} request failed: {exc}") from exc
        return raw if binary else json.loads(raw) if raw else None

    def pages(self, path):
        result = []
        for page in range(1, 10001):
            separator = "&" if "?" in path else "?"
            items = self.request("GET", f"{path}{separator}per_page=100&page={page}")
            if not isinstance(items, list):
                raise ReleaseError(f"Expected a paginated list from {path}.")
            result.extend(items)
            if len(items) < 100:
                return result
        raise ReleaseError(f"Pagination did not terminate for {path}.")

    def releases(self):
        return self.pages("/releases")

    def assets(self, release):
        return self.pages(f"/releases/{release['id']}/assets")

    def version_at(self, ref):
        if not isinstance(ref, str) or not ref:
            raise ReleaseError("Historical release is missing its source commit/ref.")
        content = self.request("GET", f"/contents/{GRADLE}?ref={urllib.parse.quote(ref, safe='')}")
        if content.get("encoding") != "base64" or content.get("type") != "file":
            raise ReleaseError(f"Cannot read version metadata at {ref}.")
        return parse_version(base64.b64decode(content["content"], validate=False).decode("utf-8"))


def remote_tags():
    refs = {}
    peeled = {}
    for line in git("ls-remote", "--tags", "origin").splitlines():
        sha, ref = line.split("\t", 1)
        name = ref.removeprefix("refs/tags/")
        target = peeled if name.endswith("^{}") else refs
        target[name.removesuffix("^{}")] = sha
    return {name: peeled.get(name, sha) for name, sha in refs.items() if TAG.fullmatch(name)}


def asset_paths(name):
    root = Path("app/build/outputs")
    return {
        f"blood-pressure-record-v{name}-release.apk": root / "apk/release" / f"blood-pressure-record-v{name}-release.apk",
        f"blood-pressure-record-v{name}-release.aab": root / "bundle/release" / f"blood-pressure-record-v{name}-release.aab",
        "release-sha256.txt": root / "release-sha256.txt",
        "release-verification.txt": root / "release-verification.txt",
    }


def asset_map(assets):
    result = {}
    for asset in assets:
        if asset["name"] in result:
            raise ReleaseError(f"Duplicate release asset: {asset['name']}.")
        result[asset["name"]] = asset
    return result


def uploaded(asset):
    return asset.get("state") == "uploaded" and isinstance(asset.get("size"), int) and asset["size"] > 0


def empty_draft_placeholder(release, asset):
    return release.get("draft") is True and asset.get("state") == "starter" and asset.get("size") == 0


def complete(release, assets, name):
    return not release.get("draft", True) and not release.get("prerelease", True) and all(
        asset in assets and uploaded(assets[asset]) for asset in asset_paths(name)
    )


def detect(api, sha, before=None):
    name, code = parse_version(Path(GRADLE).read_text(encoding="utf-8"))
    tag = f"v{name}"
    tags = remote_tags()
    releases = api.releases()
    matches = [release for release in releases if release.get("tag_name") == tag]
    if len(matches) > 1:
        raise ReleaseError(f"Multiple GitHub releases use {tag}; manual review required.")
    release = matches[0] if matches else None
    if tag in tags and tags[tag] != sha:
        raise ReleaseError(f"Tag {tag} points to {tags[tag]}, not {sha}; historical tags are never moved or overwritten.")
    if release and tag not in tags:
        raise ReleaseError(f"Release {tag} exists without its remote tag; cannot prove its source commit safely.")
    assets = asset_map(api.assets(release)) if release else {}
    if release and complete(release, assets, name):
        return {"name": name, "code": code, "tag": tag, "release": release, "assets": assets,
                "should_release": False, "reason": f"{tag} already has a complete published stable Release; no changes made."}
    if release and release.get("prerelease"):
        raise ReleaseError(f"{tag} is a prerelease; refusing to change an existing release's publication policy.")

    baselines = []
    refs = {version_tag: target for version_tag, target in tags.items() if version_tag != tag}
    for historical in releases:
        historical_tag = historical.get("tag_name", "")
        if historical_tag != tag and TAG.fullmatch(historical_tag) and not historical.get("draft"):
            refs.setdefault(historical_tag, historical.get("target_commitish"))
    for historical_tag, target in refs.items():
        prior_name, prior_code = api.version_at(target)
        if historical_tag != f"v{prior_name}":
            raise ReleaseError(f"Historical {historical_tag} does not match its source versionName {prior_name}.")
        baselines.append((prior_name, prior_code, historical_tag))

    if before and not re.fullmatch(r"0+", before):
        prior = parse_version(git("show", f"{before}:{GRADLE}"))
    else:
        parents = git("rev-list", "--parents", "-n", "1", sha).split()[1:]
        prior = parse_version(git("show", f"{parents[0]}:{GRADLE}")) if parents else None
    recovery = tag in tags
    if prior and prior != (name, code):
        baselines.append((*prior, "push baseline / first parent"))
    elif prior and not recovery:
        return {"name": name, "code": code, "tag": tag, "release": release, "assets": assets,
                "should_release": False, "reason": f"versionName/versionCode are unchanged ({name}/{code}); this is not a new version commit."}
    for prior_name, prior_code, source in baselines:
        if version_tuple(name) <= version_tuple(prior_name) or code <= prior_code:
            raise ReleaseError(f"{name}/{code} must increase both versionName and versionCode beyond {source} ({prior_name}/{prior_code}).")
    return {"name": name, "code": code, "tag": tag, "release": release, "assets": assets,
            "should_release": True, "reason": f"{'Recover' if recovery else 'Publish'} {tag} ({code}) at {sha}."}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def validate_report(data, state):
    report = data.decode("utf-8")
    if not re.search(r"^Number of signers: 1\s*$", report, re.M) or not re.search(
        rf"^Signer #1 certificate SHA-256 digest:\s*{CERTIFICATE}\s*$", report, re.I | re.M
    ):
        raise ReleaseError("Verification report does not prove the expected single APK signing certificate.")
    if f"package: name='com.yang.bloodpressure' versionCode='{state['code']}' versionName='{state['name']}'" not in report:
        raise ReleaseError("Verification report does not match the expected application/version.")
    if not re.search(r"^AAB verified signed entries: [1-9][0-9]*\s*$", report, re.M) or not re.search(
        rf"^AAB certificate SHA-256 digest:\s*{CERTIFICATE}\s*$", report, re.I | re.M
    ):
        raise ReleaseError("Verification report does not prove signed AAB payload entries and the production certificate.")
    if not re.search(r"^Verification successful\s*$", report, re.M):
        raise ReleaseError("Verification report does not contain successful APK zipalign verification.")


def validate_files(state):
    paths = asset_paths(state["name"])
    data = {}
    for name, path in paths.items():
        if not path.is_file() or path.stat().st_size == 0:
            raise ReleaseError(f"Required verified build output is missing or empty: {path}.")
        data[name] = path.read_bytes()
    package_names = [name for name in paths if name.endswith((".apk", ".aab"))]
    expected = "".join(f"{sha256(data[name])}  {paths[name].as_posix()}\n" for name in package_names)
    if data["release-sha256.txt"].decode("utf-8") != expected:
        raise ReleaseError("release-sha256.txt does not match the APK/AAB and required relative-path format.")
    validate_report(data["release-verification.txt"], state)
    return data


def restore_packages(api, sha, before=None):
    """Reuse published signed bytes locally, then let the workflow verify them again."""
    state = detect(api, sha, before)
    if not state["should_release"] or not state["release"]:
        return state
    restored = {}
    paths = asset_paths(state["name"])
    for name, path in paths.items():
        if not name.endswith((".apk", ".aab")) or name not in state["assets"]:
            continue
        asset = state["assets"][name]
        if empty_draft_placeholder(state["release"], asset):
            continue
        if not uploaded(asset):
            raise ReleaseError(f"Existing package {name} is not completely uploaded; refusing automatic recovery.")
        data = api.request("GET", f"/releases/assets/{asset['id']}", binary=True)
        if len(data) != asset["size"]:
            raise ReleaseError(f"Downloaded package {name} differs from the remote asset size.")
        digest = asset.get("digest")
        if digest and digest != f"sha256:{sha256(data)}":
            raise ReleaseError(f"Downloaded package {name} does not match GitHub's SHA-256 digest.")
        restored[path] = data
    # Download all packages successfully before replacing any local build output.
    # Signature, certificate, application version and alignment verification run
    # after this command and before publish; downloaded bytes are never trusted.
    for path, data in restored.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_name(path.name + ".restore.tmp")
        try:
            temporary.write_bytes(data)
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)
    state["reason"] = f"{state['tag']}: restored {len(restored)} existing signed package(s) locally for fresh signature/version verification."
    return state


def publish(api, sha, before=None):
    state = detect(api, sha, before)
    if not state["should_release"]:
        return state
    files = validate_files(state)
    tag = state["tag"]
    # Read again immediately before creating the ref; create-ref is atomic and
    # explicitly non-force, so a racing run cannot move an existing tag.
    tags = remote_tags()
    if tag in tags and tags[tag] != sha:
        raise ReleaseError(f"Tag {tag} changed to a different commit; refusing publication.")
    if tag not in tags:
        api.request("POST", "/git/refs", {"ref": f"refs/tags/{tag}", "sha": sha})
    release = state["release"]
    if not release:
        notes_path = Path("docs") / f"release-{tag}.md"
        body = {"tag_name": tag, "target_commitish": sha, "name": f"Release {tag}", "draft": True, "prerelease": False}
        if notes_path.is_file():
            body["body"] = notes_path.read_text(encoding="utf-8")
        else:
            body["body"] = api.request("POST", "/releases/generate-notes", {"tag_name": tag, "target_commitish": sha})["body"]
        release = api.request("POST", "/releases", body)
    existing = asset_map(api.assets(release))
    placeholders = []
    # Never clobber. Rebuilds are not assumed reproducible: a partial release
    # containing different signed bytes needs the original verified artifacts.
    for name, data in files.items():
        if name not in existing:
            continue
        asset = existing[name]
        if empty_draft_placeholder(release, asset):
            placeholders.append(asset)
            continue
        if not uploaded(asset):
            raise ReleaseError(f"Existing asset {name} is incomplete or differs in size; refusing overwrite. Restore original verified artifacts or review the draft manually.")
        remote = api.request("GET", f"/releases/assets/{asset['id']}", binary=True)
        if len(remote) != asset["size"]:
            raise ReleaseError(f"Existing asset {name} download differs from its declared size.")
        if name == "release-verification.txt":
            # Tool versions/timestamps may change report text on a rerun. The
            # original report stays untouched if it proves the same package,
            # version and production certificates; local outputs were also
            # freshly validated above for the restored signed package bytes.
            validate_report(remote, state)
            files[name] = remote
            continue
        if asset["size"] != len(data):
            raise ReleaseError(f"Existing asset {name} differs in size; refusing overwrite. Retry with the original verified artifacts.")
        if sha256(remote) != sha256(data):
            raise ReleaseError(f"Existing asset {name} differs from this build; refusing overwrite. Retry with the original verified artifacts.")
    if placeholders:
        # GitHub may leave a zero-byte 'starter' asset after a failed upload.
        # Recheck both release and assets before deleting only these empty draft
        # placeholders. Never delete any uploaded/nonempty/published asset.
        latest = api.request("GET", f"/releases/{release['id']}")
        latest_assets = asset_map(api.assets(latest))
        if latest.get("draft") is not True or latest.get("tag_name") != tag or remote_tags().get(tag) != sha:
            raise ReleaseError("Release/tag changed before empty draft placeholder recovery; refusing deletion.")
        for asset in placeholders:
            current = latest_assets.get(asset["name"])
            if not current or current["id"] != asset["id"] or not empty_draft_placeholder(latest, current):
                raise ReleaseError(f"Draft placeholder {asset['name']} changed; refusing deletion.")
        for asset in placeholders:
            api.request("DELETE", f"/releases/assets/{asset['id']}")
            existing.pop(asset["name"])
    for name, data in files.items():
        if name not in existing:
            url = release["upload_url"].split("{", 1)[0] + "?name=" + urllib.parse.quote(name)
            api.request("POST", url, data, content_type="application/octet-stream")
    final_assets = asset_map(api.assets(release))
    for name, data in files.items():
        asset = final_assets.get(name)
        if not asset or not uploaded(asset) or asset["size"] != len(data):
            raise ReleaseError(f"Asset {name} is not completely uploaded; Release remains unpublished if draft.")
        if sha256(api.request("GET", f"/releases/assets/{asset['id']}", binary=True)) != sha256(data):
            raise ReleaseError(f"Uploaded asset {name} failed SHA-256 verification; refusing publication.")
    if remote_tags().get(tag) != sha:
        raise ReleaseError(f"Remote tag {tag} no longer resolves to the build commit; refusing publication.")
    if release.get("draft"):
        api.request("PATCH", f"/releases/{release['id']}", {"draft": False})
    # Verify the persisted final publication, including pagination and stable state.
    final = [item for item in api.releases() if item.get("tag_name") == tag]
    if len(final) != 1 or not complete(final[0], asset_map(api.assets(final[0])), state["name"]):
        raise ReleaseError(f"GitHub did not confirm a complete stable published Release for {tag}.")
    state["reason"] = f"{tag}: all four assets verified; {'draft published' if release.get('draft') else 'missing assets restored without changing existing release metadata'}."
    return state


def emit(state):
    print(state["reason"])
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            handle.write(f"should_release={str(state['should_release']).lower()}\n")
    env = os.environ.get("GITHUB_ENV")
    if env:
        with open(env, "a", encoding="utf-8") as handle:
            handle.write(f"VERSION_NAME={state['name']}\nVERSION_CODE={state['code']}\nRELEASE_TAG={state['tag']}\n")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(f"- {state['reason']}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("detect", "restore-packages", "publish"))
    args = parser.parse_args()
    try:
        sha = os.environ.get("GITHUB_SHA", "")
        if not re.fullmatch(r"[0-9a-f]{40}", sha):
            raise ReleaseError("GITHUB_SHA must be the full build commit SHA.")
        if git("rev-parse", "HEAD") != sha:
            raise ReleaseError("Checkout HEAD differs from GITHUB_SHA.")
        api = GitHub(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
        commands = {"detect": detect, "restore-packages": restore_packages, "publish": publish}
        emit(commands[args.command](api, sha, os.environ.get("GITHUB_EVENT_BEFORE")))
    except (ReleaseError, ValueError, KeyError, UnicodeError, OSError) as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
