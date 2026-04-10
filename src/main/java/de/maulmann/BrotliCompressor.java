package de.maulmann;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;
import com.aayushatharva.brotli4j.encoder.Encoder;

import java.io.*;
import java.nio.file.Files;

public class BrotliCompressor {

    static {
        Brotli4jLoader.ensureAvailability();
    }

    // Default compression level for Brotli (usually 4 is a good balance, but let's use 11 for "best" as requested by context of 9 in GZIP)
    public static final int DEFAULT_QUALITY = 4;
    public static final int BEST_QUALITY = 11;

    // --- 1. File-to-File Method ---
    public static void compressFile(File inputFile, File outputFile, int quality) throws IOException {
        Encoder.Parameters params = new Encoder.Parameters().setQuality(quality);
        try (InputStream in = Files.newInputStream(inputFile.toPath());
             OutputStream out = Files.newOutputStream(outputFile.toPath());
             BrotliOutputStream brOut = new BrotliOutputStream(out, params)) {

            byte[] buffer = new byte[65536];
            int len;
            while ((len = in.read(buffer)) != -1) {
                brOut.write(buffer, 0, len);
            }
        }
    }

    // --- 2. RAM-to-File Method ---
    public static void compressBytesToFile(byte[] inputData, File outputFile, int quality) throws IOException {
        Encoder.Parameters params = new Encoder.Parameters().setQuality(quality);
        try (OutputStream out = Files.newOutputStream(outputFile.toPath());
             BrotliOutputStream brOut = new BrotliOutputStream(out, params)) {
            brOut.write(inputData);
        }
    }

    // --- 3. Pure In-Memory Method ---
    public static byte[] compressBytes(byte[] inputData, int quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Encoder.Parameters params = new Encoder.Parameters().setQuality(quality);
        try (BrotliOutputStream brOut = new BrotliOutputStream(baos, params)) {
            brOut.write(inputData);
        }
        return baos.toByteArray();
    }

    // Default override
    public static void compressFile(File inputFile, File outputFile) throws IOException {
        compressFile(inputFile, outputFile, DEFAULT_QUALITY);
    }
}
