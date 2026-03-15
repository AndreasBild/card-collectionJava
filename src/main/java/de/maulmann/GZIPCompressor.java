package de.maulmann;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class GZIPCompressor {

    public static final int BEST_COMPRESSION = Deflater.BEST_COMPRESSION;
    public static final int BEST_SPEED = Deflater.BEST_SPEED;
    public static final int DEFAULT_COMPRESSION = Deflater.DEFAULT_COMPRESSION;
    public static final int NO_COMPRESSION = Deflater.NO_COMPRESSION;

    // --- 1. Upgraded File-to-File Method ---
    public static void compressFile(File inputFile, File outputFile, int compressionLevel) throws IOException {
        try (InputStream in = Files.newInputStream(inputFile.toPath());
             OutputStream out = Files.newOutputStream(outputFile.toPath());
             // Set internal GZIP buffer to 64KB for faster memory allocation
             GZIPOutputStream gzipOS = new GZIPOutputStream(out, 65536) {
                 {
                     def.setLevel(compressionLevel);
                 }
             }) {

            byte[] buffer = new byte[65536]; // Increased from 1KB to 64KB
            int len;
            while ((len = in.read(buffer)) != -1) {
                gzipOS.write(buffer, 0, len);
            }
        }
    }

    // --- 2. NEW: RAM-to-File Method ---
    // Takes the byte[] from your minifier and writes directly to the final .gz file
    public static void compressBytesToFile(byte[] inputData, File outputFile, int compressionLevel) throws IOException {
        try (OutputStream out = Files.newOutputStream(outputFile.toPath());
             GZIPOutputStream gzipOS = new GZIPOutputStream(out, 65536) {
                 {
                     def.setLevel(compressionLevel);
                 }
             }) {
            gzipOS.write(inputData);
        }
    }

    // --- 3. NEW: Pure In-Memory Method ---
    // Takes a byte[] and returns a compressed byte[]. Zero disk I/O.
    public static byte[] compressBytes(byte[] inputData, int compressionLevel) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(inputData.length);
        try (GZIPOutputStream gzipOS = new GZIPOutputStream(baos) {
            {
                def.setLevel(compressionLevel);
            }
        }) {
            gzipOS.write(inputData);
        }
        return baos.toByteArray();
    }

    // Default override
    public static void compressFile(File inputFile, File outputFile) throws IOException {
        compressFile(inputFile, outputFile, DEFAULT_COMPRESSION);
    }
}