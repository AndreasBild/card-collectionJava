package de.maulmann;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Centralized MD5 hashing helpers (lowercase hex output).
 * MD5 is used purely for change detection and stable IDs, never for security.
 * Every Java platform implementation is required to provide MD5, so the checked exception is unreachable.
 */
public final class HashUtils {

    private HashUtils() {}

    private static MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required by the Java platform specification", e);
        }
    }

    /** MD5 of the UTF-8 bytes of {@code content} as 32 lowercase hex characters. */
    public static String md5Hex(String content) {
        return HexFormat.of().formatHex(md5().digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    /** MD5 of the file content as 32 lowercase hex characters (streamed, constant memory). */
    public static String md5Hex(Path file) throws IOException {
        MessageDigest md = md5();
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
