"""Offline tests for automatic release version planning; no GitHub writes."""
import unittest
from auto_version import choose_version, rewrite_gradle
from release_ci import ReleaseError, parse_version


class AutoVersionTests(unittest.TestCase):
    def test_new_merge_bumps_patch_and_code(self):
        self.assertEqual(choose_version("2.3.3", 44, "2.3.3", 44), ("2.3.4", 45, True))

    def test_released_commit_does_not_republish(self):
        self.assertEqual(choose_version("2.3.3", 44, "2.3.3", 44, True), ("2.3.3", 44, False))

    def test_explicit_higher_version_is_preserved(self):
        self.assertEqual(choose_version("2.4.0", 46, "2.3.3", 44), ("2.4.0", 46, False))

    def test_only_one_field_advanced_rejected(self):
        for name, code in (("2.3.4", 44), ("2.3.3", 45), ("2.3.2", 50), ("2.3.4", 43)):
            with self.subTest(name=name, code=code), self.assertRaises(ReleaseError):
                choose_version(name, code, "2.3.3", 44)

    def test_code_limit_fails_closed(self):
        with self.assertRaises(ReleaseError):
            choose_version("2.3.3", 2100000000, "2.3.3", 2100000000)

    def test_rewrite_only_two_literal_fields(self):
        source = 'versionCode = 44\nversionName = "2.3.3"\n// versionName = "9.9.9"\n'
        updated = rewrite_gradle(source, "2.3.4", 45)
        self.assertEqual(parse_version(updated), ("2.3.4", 45))
        self.assertIn('// versionName = "9.9.9"', updated)

    def test_rewrite_refuses_multiple_assignments(self):
        with self.assertRaises(ReleaseError):
            rewrite_gradle('versionCode = 44\nversionCode = 45\nversionName = "2.3.3"\n', "2.3.4", 45)


if __name__ == "__main__":
    unittest.main()
