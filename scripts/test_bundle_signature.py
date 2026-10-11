"""Exercise actual JDK verification with disposable test certificates/bundles."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile


@unittest.skipUnless(all(shutil.which(x) for x in ("java", "keytool", "jarsigner")), "JDK 17 required")
class BundleSignatureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        cls.verifier = Path(__file__).with_name("VerifyReleaseBundle.java").resolve()
        cls.keystore = cls.root / "test.jks"
        subprocess.run([
            "keytool", "-genkeypair", "-alias", "test", "-keyalg", "RSA",
            "-keystore", str(cls.keystore), "-storepass", "test-only-password",
            "-keypass", "test-only-password", "-dname", "CN=Disposable CI Test",
            "-validity", "1", "-noprompt",
        ], check=True, capture_output=True)
        cert = subprocess.run([
            "keytool", "-exportcert", "-alias", "test", "-keystore", str(cls.keystore),
            "-storepass", "test-only-password",
        ], check=True, capture_output=True).stdout
        cls.fingerprint = hashlib.sha256(cert).hexdigest()
        cls.signed = cls.root / "signed.aab"
        cls.make_bundle(cls.signed)
        subprocess.run([
            "jarsigner", "-keystore", str(cls.keystore), "-storepass", "test-only-password",
            str(cls.signed), "test",
        ], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    @staticmethod
    def make_bundle(path):
        with zipfile.ZipFile(path, "w") as bundle:
            bundle.writestr("base/manifest/AndroidManifest.xml", b"test manifest")
            bundle.writestr("base/dex/classes.dex", b"test payload")

    def verify(self, path, fingerprint=None):
        return subprocess.run([
            "java", str(self.verifier), str(path), fingerprint or self.fingerprint,
        ], capture_output=True, text=True)

    def test_signed_payload_passes(self):
        result = self.verify(self.signed)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("AAB certificate SHA-256 digest: " + self.fingerprint, result.stdout)

    def test_unsigned_bundle_fails(self):
        path = self.root / "unsigned.aab"
        self.make_bundle(path)
        self.assertNotEqual(self.verify(path).returncode, 0)

    def test_wrong_certificate_fails(self):
        self.assertNotEqual(self.verify(self.signed, "0" * 64).returncode, 0)

    def test_unsigned_added_entry_fails(self):
        path = self.root / "partial.aab"
        shutil.copyfile(self.signed, path)
        with zipfile.ZipFile(path, "a") as bundle:
            bundle.writestr("META-INF/unsigned-payload.txt", "not signed")
        self.assertNotEqual(self.verify(path).returncode, 0)

    def test_modified_signed_entry_fails(self):
        path = self.root / "modified.aab"
        with zipfile.ZipFile(self.signed) as source, zipfile.ZipFile(path, "w") as target:
            for entry in source.infolist():
                data = source.read(entry)
                if entry.filename == "base/dex/classes.dex":
                    data = b"altered payload"
                target.writestr(entry, data)
        self.assertNotEqual(self.verify(path).returncode, 0)


if __name__ == "__main__":
    unittest.main()
