import java.io.InputStream;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Fail closed for unsigned, partially signed, altered, or incorrectly signed AABs. */
class VerifyReleaseBundle {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Usage: VerifyReleaseBundle.java bundle.aab expected-sha256");
        }
        int verified = 0;
        Set<String> names = new HashSet<>();
        try (JarFile bundle = new JarFile(args[0], true)) {
            var entries = bundle.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!names.add(entry.getName())) {
                    throw new SecurityException("Duplicate bundle entry: " + entry.getName());
                }
                if (entry.isDirectory()) continue;
                // Only JAR signature metadata is exempt. Other META-INF payloads
                // (including Android Gradle metadata) must also be signed.
                String name = entry.getName().toUpperCase(java.util.Locale.ROOT);
                if (name.equals("META-INF/MANIFEST.MF") ||
                    name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)") ||
                    name.matches("META-INF/SIG-[^/]+")) continue;
                try (InputStream input = bundle.getInputStream(entry)) {
                    input.transferTo(java.io.OutputStream.nullOutputStream());
                }
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers == null || signers.length != 1) {
                    throw new SecurityException("Expected one production signer for " + entry.getName());
                }
                byte[] certificate = signers[0].getSignerCertPath().getCertificates().get(0).getEncoded();
                String fingerprint = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate));
                if (!fingerprint.equals(args[1])) {
                    throw new SecurityException("Unexpected bundle certificate for " + entry.getName());
                }
                verified++;
            }
        }
        if (verified == 0 || !names.contains("base/manifest/AndroidManifest.xml")) {
            throw new SecurityException("Bundle has no signed Android application payload");
        }
        System.out.println("AAB verified signed entries: " + verified);
        System.out.println("AAB certificate SHA-256 digest: " + args[1]);
    }
}
