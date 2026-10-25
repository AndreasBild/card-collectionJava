package de.maulmann;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashUtilsTest {

    // RFC 1321 test vectors
    private static final String MD5_EMPTY = "d41d8cd98f00b204e9800998ecf8427e";
    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";

    @TempDir
    Path tempDir;

    @Test
    void md5HexOfStringMatchesKnownVectors() {
        assertEquals(MD5_EMPTY, HashUtils.md5Hex(""));
        assertEquals(MD5_ABC, HashUtils.md5Hex("abc"));
    }

    @Test
    void md5HexOfFileMatchesStringHash() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);
        assertEquals(MD5_ABC, HashUtils.md5Hex(file));
    }

    @Test
    void md5HexIsLowercaseAndZeroPadded() {
        String hash = HashUtils.md5Hex("any content");
        assertEquals(32, hash.length());
        assertEquals(hash.toLowerCase(), hash);
    }

    @Test
    void stableIdIsFirstEightHexCharsOfMd5() {
        // "Player:Juwan Howard|" hashed with MD5; stable ID must equal the first 8 hex chars.
        String expected = HashUtils.md5Hex("Player:Juwan Howard|").substring(0, 8);
        assertEquals(expected, CardData.generateStableId(java.util.Map.of("Player", "Juwan Howard")));
    }
}
