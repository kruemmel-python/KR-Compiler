import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

/** Exports Android apksig-compatible PKCS#8 and DER files from a local keystore. */
public final class ExportSigningMaterial {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                    "Usage: <keystore> <store-password> <alias> <key-password> <key.pk8> <cert.der>");
        }

        char[] storePassword = args[1].toCharArray();
        char[] keyPassword = args[3].toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(args[0]))) {
            store.load(input, storePassword);
        }

        Key key = store.getKey(args[2], keyPassword);
        Certificate certificate = store.getCertificate(args[2]);
        if (!(key instanceof PrivateKey) || certificate == null) {
            throw new IllegalStateException("Private key or certificate is missing for alias " + args[2]);
        }

        Path keyOutput = Path.of(args[4]);
        Path certificateOutput = Path.of(args[5]);
        Files.createDirectories(keyOutput.getParent());
        Files.createDirectories(certificateOutput.getParent());
        Files.write(keyOutput, key.getEncoded());
        Files.write(certificateOutput, certificate.getEncoded());
    }
}
