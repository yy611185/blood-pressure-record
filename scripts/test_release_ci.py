"""Offline tests: no GitHub writes and no Android build/emulator required."""

import copy
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

import release_ci as ci


SHA = "a" * 40
OLD_SHA = "b" * 40


def gradle(name="2.3.2", code=43):
    return f'versionName = "{name}"\nversionCode = {code}\n'


class FakeGitHub:
    def __init__(self):
        self.release_list = []
        self.asset_list = []
        self.asset_data = {}
        self.versions = {OLD_SHA: ("2.3.1", 42)}
        self.calls = []
        self.tags = {"v2.3.1": OLD_SHA}
        self.fail_upload = None
        self.corrupt_upload = None

    def releases(self):
        return copy.deepcopy(self.release_list)

    def assets(self, release):
        return copy.deepcopy(self.asset_list)

    def version_at(self, ref):
        return self.versions[ref]

    def request(self, method, path, body=None, **kwargs):
        self.calls.append((method, path, body))
        if path == "/git/refs":
            self.tags[body["ref"].removeprefix("refs/tags/")] = body["sha"]
            return {}
        if path == "/releases/generate-notes":
            return {"body": "Generated notes"}
        if path == "/releases" and method == "POST":
            release = {**body, "id": 10, "upload_url": "https://uploads.github.com/repos/owner/repo/releases/10/assets{?name,label}"}
            self.release_list.append(release)
            return copy.deepcopy(release)
        if method == "POST" and "uploads.github.com" in path:
            name = ci.urllib.parse.parse_qs(ci.urllib.parse.urlparse(path).query)["name"][0]
            if name == self.fail_upload:
                raise ci.ReleaseError("Simulated interrupted upload")
            asset = {"id": len(self.asset_list) + 1, "name": name, "size": len(body), "state": "uploaded"}
            self.asset_list.append(asset)
            self.asset_data[asset["id"]] = b"corrupt" if name == self.corrupt_upload else body
            return asset
        if method == "GET" and path.startswith("/releases/assets/"):
            return self.asset_data[int(path.rsplit("/", 1)[1])]
        if method == "DELETE" and path.startswith("/releases/assets/"):
            asset_id = int(path.rsplit("/", 1)[1])
            self.asset_list = [asset for asset in self.asset_list if asset["id"] != asset_id]
            self.asset_data.pop(asset_id, None)
            return None
        if method == "GET" and path == "/releases/10":
            return copy.deepcopy(self.release_list[0])
        if method == "PATCH":
            self.release_list[0].update(body)
            return self.release_list[0]
        raise AssertionError((method, path))

    def add_release(self, *, draft=False, prerelease=False):
        self.release_list = [{"id": 10, "tag_name": "v2.3.2", "draft": draft, "prerelease": prerelease,
                              "target_commitish": SHA, "name": "Existing title", "body": "Existing notes",
                              "upload_url": "https://uploads.github.com/repos/owner/repo/releases/10/assets{?name,label}"}]
        self.tags["v2.3.2"] = SHA

    def add_asset(self, name, data=b"existing"):
        asset_id = len(self.asset_list) + 1
        self.asset_list.append({"id": asset_id, "name": name, "state": "uploaded", "size": len(data)})
        self.asset_data[asset_id] = data


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.previous_cwd = os.getcwd()
        self.directory = tempfile.TemporaryDirectory()
        os.chdir(self.directory.name)
        Path(ci.GRADLE).parent.mkdir(parents=True)
        Path(ci.GRADLE).write_text(gradle(), encoding="utf-8")
        self.api = FakeGitHub()
        self.git_patch = patch.object(ci, "git", side_effect=self.fake_git)
        self.tags_patch = patch.object(ci, "remote_tags", side_effect=lambda: dict(self.api.tags))
        self.git_patch.start()
        self.tags_patch.start()

    def tearDown(self):
        self.git_patch.stop()
        self.tags_patch.stop()
        os.chdir(self.previous_cwd)
        self.directory.cleanup()

    def fake_git(self, *args):
        if args[0] == "show":
            return gradle("2.3.1", 42)
        if args[0] == "rev-list":
            return f"{SHA} {OLD_SHA}"
        raise AssertionError(args)

    def write_outputs(self):
        state = {"name": "2.3.2", "code": 43}
        paths = ci.asset_paths("2.3.2")
        for name, path in paths.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            if name.endswith((".apk", ".aab")):
                path.write_bytes(f"signed {name}".encode())
        paths["release-sha256.txt"].write_text("".join(
            f"{ci.sha256(path.read_bytes())}  {path.as_posix()}\n"
            for name, path in paths.items() if name.endswith((".apk", ".aab"))
        ), encoding="utf-8", newline="\n")
        paths["release-verification.txt"].write_text(
            f"Number of signers: 1\nSigner #1 certificate SHA-256 digest: {ci.CERTIFICATE}\n"
            "package: name='com.yang.bloodpressure' versionCode='43' versionName='2.3.2'\n"
            f"AAB verified signed entries: 100\nAAB certificate SHA-256 digest: {ci.CERTIFICATE}\n"
            "Verification successful\n",
            encoding="utf-8", newline="\n",
        )
        return ci.validate_files(state)

    def test_strict_version_format_and_unique_literals(self):
        self.assertEqual(ci.parse_version(gradle()), ("2.3.2", 43))
        for source in (gradle("02.3.2"), gradle("2.3"), gradle(code=0), gradle(code=2100000001),
                       gradle() + 'versionName = "2.3.3"\n', 'versionName = provider()\nversionCode = 43',
                       'versionName = "2.3.2"\nversionCode = 43 + 1'):
            with self.subTest(source=source), self.assertRaises(ci.ReleaseError):
                ci.parse_version(source)

    def test_new_version_passes_both_increment_checks(self):
        self.assertTrue(ci.detect(self.api, SHA)["should_release"])
        for name, code in (("2.3.2", 42), ("2.3.1", 43), ("2.2.9", 44)):
            Path(ci.GRADLE).write_text(gradle(name, code), encoding="utf-8")
            with self.subTest(name=name, code=code), self.assertRaises(ci.ReleaseError):
                ci.detect(self.api, SHA)

    def test_push_before_is_used_for_batched_commits(self):
        with patch.object(ci, "git", return_value=gradle("2.3.1", 42)) as mock:
            self.assertTrue(ci.detect(self.api, SHA, OLD_SHA)["should_release"])
        mock.assert_called_once_with("show", f"{OLD_SHA}:{ci.GRADLE}")

    def test_unchanged_version_without_tag_skips(self):
        with patch.object(ci, "git", side_effect=lambda *args: gradle() if args[0] == "show" else f"{SHA} {OLD_SHA}"):
            self.assertFalse(ci.detect(self.api, SHA)["should_release"])

    def test_complete_release_skips_and_conflict_takes_priority(self):
        self.api.add_release()
        for name in ci.asset_paths("2.3.2"):
            self.api.add_asset(name)
        self.assertFalse(ci.detect(self.api, SHA)["should_release"])
        self.assertFalse(ci.publish(self.api, SHA)["should_release"])
        self.assertEqual(self.api.calls, [])
        self.api.tags["v2.3.2"] = OLD_SHA
        with self.assertRaisesRegex(ci.ReleaseError, "never moved"):
            ci.detect(self.api, SHA)

    def test_same_sha_tag_without_release_allows_rerun(self):
        self.api.tags["v2.3.2"] = SHA
        with patch.object(ci, "git", side_effect=lambda *args: gradle() if args[0] == "show" else f"{SHA} {OLD_SHA}"):
            self.assertTrue(ci.detect(self.api, SHA)["should_release"])

    def test_historical_tags_and_releases_both_form_baseline(self):
        self.api.release_list = [{"tag_name": "v2.4.0", "target_commitish": "future", "draft": False}]
        self.api.versions["future"] = ("2.4.0", 44)
        with self.assertRaisesRegex(ci.ReleaseError, "v2.4.0"):
            ci.detect(self.api, SHA)

    def test_remote_errors_are_not_absence(self):
        with patch.object(self.api, "releases", side_effect=ci.ReleaseError("HTTP 403")):
            with self.assertRaisesRegex(ci.ReleaseError, "403"):
                ci.detect(self.api, SHA)

    def test_duplicate_assets_and_orphan_release_fail(self):
        self.api.add_release()
        self.api.add_asset("release-sha256.txt")
        self.api.add_asset("release-sha256.txt")
        with self.assertRaisesRegex(ci.ReleaseError, "Duplicate"):
            ci.detect(self.api, SHA)
        self.api.tags.pop("v2.3.2")
        with self.assertRaisesRegex(ci.ReleaseError, "without its remote tag"):
            ci.detect(self.api, SHA)

    def test_new_release_creates_tag_then_draft_and_only_publishes_after_uploads(self):
        self.write_outputs()
        self.assertTrue(ci.publish(self.api, SHA)["should_release"])
        self.assertFalse(self.api.release_list[0]["draft"])
        self.assertEqual(len(self.api.asset_list), 4)
        mutations = [(method, path) for method, path, _ in self.api.calls if method in ("POST", "PATCH")]
        self.assertEqual(mutations[0], ("POST", "/git/refs"))
        self.assertEqual(mutations[-1], ("PATCH", "/releases/10"))

    def test_draft_upload_interruption_safely_resumes(self):
        self.write_outputs()
        self.api.fail_upload = "release-sha256.txt"
        with self.assertRaisesRegex(ci.ReleaseError, "interrupted"):
            ci.publish(self.api, SHA)
        self.assertTrue(self.api.release_list[0]["draft"])
        self.assertEqual(len(self.api.asset_list), 2)
        self.api.fail_upload = None
        ci.publish(self.api, SHA)
        self.assertFalse(self.api.release_list[0]["draft"])
        self.assertEqual(len(self.api.asset_list), 4)
        self.assertEqual(sum(path == "/git/refs" for _, path, _ in self.api.calls), 1)

    def test_restore_packages_preserves_original_signed_bytes_for_rerun(self):
        files = self.write_outputs()
        self.api.add_release(draft=True)
        package = "blood-pressure-record-v2.3.2-release.aab"
        self.api.add_asset(package, files[package])
        ci.asset_paths("2.3.2")[package].write_bytes(b"new build contains a different signature timestamp")
        ci.restore_packages(self.api, SHA)
        self.assertEqual(ci.asset_paths("2.3.2")[package].read_bytes(), files[package])
        ci.publish(self.api, SHA)
        self.assertFalse(self.api.release_list[0]["draft"])

    def test_restore_rejects_bad_states_sizes_digests_and_conflicts(self):
        self.write_outputs()
        self.api.add_release(draft=True)
        package = "blood-pressure-record-v2.3.2-release.apk"
        self.api.add_asset(package, b"original bytes")
        for changes in ({"state": "starter"}, {"size": 999}, {"digest": "sha256:bad"}):
            with self.subTest(changes=changes):
                original = copy.deepcopy(self.api.asset_list[0])
                self.api.asset_list[0].update(changes)
                with self.assertRaises(ci.ReleaseError):
                    ci.restore_packages(self.api, SHA)
                self.api.asset_list[0] = original
        self.api.tags["v2.3.2"] = OLD_SHA
        with self.assertRaisesRegex(ci.ReleaseError, "never moved"):
            ci.restore_packages(self.api, SHA)
        self.assertFalse(any(method != "GET" for method, _, _ in self.api.calls))

    def test_existing_report_is_preserved_when_equivalent_proof_text_changes(self):
        files = self.write_outputs()
        self.api.add_release(draft=True)
        old_report = files["release-verification.txt"] + b"Original build report diagnostics\n"
        self.api.add_asset("release-verification.txt", old_report)
        ci.publish(self.api, SHA)
        self.assertEqual(self.api.asset_data[1], old_report)
        self.assertEqual(len(self.api.asset_list), 4)

    def test_interrupted_empty_draft_starter_can_be_replaced(self):
        self.write_outputs()
        self.api.add_release(draft=True)
        package = "blood-pressure-record-v2.3.2-release.apk"
        self.api.asset_list = [{"id": 101, "name": package, "state": "starter", "size": 0}]
        ci.restore_packages(self.api, SHA)
        ci.publish(self.api, SHA)
        self.assertIn(("DELETE", "/releases/assets/101", None), self.api.calls)
        self.assertFalse(self.api.release_list[0]["draft"])
        self.assertEqual(len(self.api.asset_list), 4)

    def test_uploaded_nonempty_or_published_assets_are_never_deleted(self):
        self.write_outputs()
        package = "blood-pressure-record-v2.3.2-release.apk"
        for draft, asset_state, size in ((False, "starter", 0), (True, "uploaded", 0), (True, "starter", 1)):
            with self.subTest(draft=draft, state=asset_state, size=size):
                self.api = FakeGitHub()
                self.api.add_release(draft=draft)
                self.api.asset_list = [{"id": 101, "name": package, "state": asset_state, "size": size}]
                with self.assertRaises(ci.ReleaseError):
                    ci.publish(self.api, SHA)
                self.assertFalse(any(method == "DELETE" for method, _, _ in self.api.calls))

    def test_placeholder_recovery_rechecks_release_is_still_draft(self):
        self.write_outputs()
        self.api.add_release(draft=True)
        self.api.asset_list = [{"id": 101, "name": "release-sha256.txt", "state": "starter", "size": 0}]
        original = self.api.request
        def request(method, path, body=None, **kwargs):
            if method == "GET" and path == "/releases/10":
                return {**self.api.release_list[0], "draft": False}
            return original(method, path, body, **kwargs)
        with patch.object(self.api, "request", side_effect=request):
            with self.assertRaisesRegex(ci.ReleaseError, "refusing deletion"):
                ci.publish(self.api, SHA)
        self.assertFalse(any(method in ("POST", "PATCH", "DELETE") for method, _, _ in self.api.calls))

    def test_report_requires_aab_production_certificate_and_zipalign(self):
        files = self.write_outputs()
        for missing in (f"AAB certificate SHA-256 digest: {ci.CERTIFICATE}\n", "AAB verified signed entries: 100\n", "Verification successful\n"):
            with self.subTest(missing=missing), self.assertRaises(ci.ReleaseError):
                ci.validate_report(files["release-verification.txt"].decode().replace(missing, "").encode(), {"name": "2.3.2", "code": 43})

    def test_published_incomplete_release_only_adds_missing_assets(self):
        files = self.write_outputs()
        self.api.add_release()
        for name, data in files.items():
            if name.endswith((".apk", ".aab")):
                self.api.add_asset(name, data)
        original = copy.deepcopy(self.api.release_list)
        ci.publish(self.api, SHA)
        self.assertEqual(self.api.release_list, original)
        self.assertEqual(len(self.api.asset_list), 4)
        self.assertFalse(any(method == "PATCH" for method, _, _ in self.api.calls))

    def test_existing_build_mismatch_is_never_overwritten(self):
        self.write_outputs()
        self.api.add_release(draft=True)
        self.api.add_asset("blood-pressure-record-v2.3.2-release.apk", b"different build")
        with self.assertRaisesRegex(ci.ReleaseError, "refusing overwrite"):
            ci.publish(self.api, SHA)
        self.assertFalse(any(method in ("POST", "PATCH", "DELETE") for method, _, _ in self.api.calls))
        self.assertTrue(self.api.release_list[0]["draft"])

    def test_bad_checksum_or_certificate_cannot_create_tag_or_release(self):
        self.write_outputs()
        paths = ci.asset_paths("2.3.2")
        paths["release-sha256.txt"].write_text("bad checksum\n", encoding="utf-8")
        with self.assertRaisesRegex(ci.ReleaseError, "relative-path"):
            ci.publish(self.api, SHA)
        self.assertEqual(self.api.calls, [])
        self.write_outputs()
        paths["release-verification.txt"].write_text("unsigned", encoding="utf-8")
        with self.assertRaisesRegex(ci.ReleaseError, "signing certificate"):
            ci.publish(self.api, SHA)
        self.assertEqual(self.api.calls, [])

    def test_corrupted_upload_stays_draft(self):
        self.write_outputs()
        self.api.corrupt_upload = "release-sha256.txt"
        with self.assertRaisesRegex(ci.ReleaseError, "SHA-256 verification"):
            ci.publish(self.api, SHA)
        self.assertTrue(self.api.release_list[0]["draft"])
        self.assertFalse(any(method == "PATCH" for method, _, _ in self.api.calls))

    def test_tag_conflict_between_detection_and_publish_stops(self):
        self.write_outputs()
        snapshots = [dict(self.api.tags), {**self.api.tags, "v2.3.2": OLD_SHA}]
        with patch.object(ci, "remote_tags", side_effect=snapshots):
            with self.assertRaisesRegex(ci.ReleaseError, "changed"):
                ci.publish(self.api, SHA)
        self.assertEqual(self.api.calls, [])

    def test_action_outputs_and_summary(self):
        paths = {key: str(Path(key)) for key in ("GITHUB_OUTPUT", "GITHUB_ENV", "GITHUB_STEP_SUMMARY")}
        with patch.dict(os.environ, paths):
            ci.emit(ci.detect(self.api, SHA))
        self.assertEqual(Path("GITHUB_OUTPUT").read_text(), "should_release=true\n")
        self.assertIn("RELEASE_TAG=v2.3.2", Path("GITHUB_ENV").read_text())
        self.assertIn("Publish v2.3.2", Path("GITHUB_STEP_SUMMARY").read_text())


class ApiTests(unittest.TestCase):
    def test_releases_and_assets_paginate_beyond_one_hundred(self):
        api = ci.GitHub("owner/repo", "test-token")
        with patch.object(api, "request", side_effect=[[{}] * 100, [{"id": 101}]]) as mock:
            self.assertEqual(len(api.releases()), 101)
            self.assertIn("page=2", mock.call_args.args[1])
        with patch.object(api, "request", side_effect=[[{}] * 100, [{"id": 101}]]) as mock:
            self.assertEqual(len(api.assets({"id": 10})), 101)
            self.assertIn("/releases/10/assets", mock.call_args.args[1])

    def test_annotated_tags_use_peeled_commit_and_ignore_nonversion_tags(self):
        output = f"{'c' * 40}\trefs/tags/v2.3.2\n{SHA}\trefs/tags/v2.3.2^{{}}\n{OLD_SHA}\trefs/tags/archive/backup\n"
        with patch.object(ci, "git", return_value=output):
            self.assertEqual(ci.remote_tags(), {"v2.3.2": SHA})

    def test_http_404_403_and_network_errors_all_fail_closed(self):
        api = ci.GitHub("owner/repo", "test-token")
        for status in (404, 403, 500):
            error = urllib.error.HTTPError(api.base, status, "error", {}, io.BytesIO(b"error"))
            with patch.object(api.opener, "open", side_effect=error):
                with self.assertRaisesRegex(ci.ReleaseError, str(status)):
                    api.releases()
        with patch.object(api.opener, "open", side_effect=urllib.error.URLError("network down")):
            with self.assertRaises(ci.ReleaseError):
                api.releases()

    def test_api_response_and_binary_asset(self):
        api = ci.GitHub("owner/repo", "test-token")
        for binary, payload in ((False, b'{"id": 10}'), (True, b"signed apk")):
            response = io.BytesIO(payload)
            with patch.object(api.opener, "open", return_value=response):
                result = api.request("GET", "/releases/assets/1", binary=binary)
            self.assertEqual(result, payload if binary else {"id": 10})

    def test_asset_redirect_strips_authorization_and_rejects_untrusted_urls(self):
        handler = ci.SafeRedirectHandler()
        request = ci.urllib.request.Request("https://api.github.com/repos/owner/repo/releases/assets/1", headers={"Authorization": "Bearer test-token"})
        redirected = handler.redirect_request(request, None, 302, "Found", {}, "https://release-assets.githubusercontent.com/signed-asset?sig=example")
        self.assertFalse(any(key.lower() == "authorization" for key in redirected.headers))
        same_host = handler.redirect_request(request, None, 302, "Found", {}, "https://api.github.com/repos/owner/renamed/releases/assets/1")
        self.assertEqual(same_host.get_header("Authorization"), "Bearer test-token")
        for url in ("http://release-assets.githubusercontent.com/asset", "https://untrusted.example/asset", "https://githubusercontent.com.evil.example/asset"):
            with self.subTest(url=url), self.assertRaises(ci.ReleaseError):
                handler.redirect_request(request, None, 302, "Found", {}, url)


if __name__ == "__main__":
    unittest.main()
