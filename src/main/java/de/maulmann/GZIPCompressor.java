package de.maulmann;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class GZIPCompressor {
    public static void compressFile(File inputFile, File outputFile, int compressionLevel) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile);
             GZIPOutputStream gzipOS = new GZIPOutputStream(fos) {
                 {
                     def.setLevel(compressionLevel);
                 }
             }) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                gzipOS.write(buffer, 0, len);
            }
        }
    }

    public static void compressFile(File inputFile, File outputFile) throws IOException {
        compressFile(inputFile, outputFile, Deflater.DEFAULT_COMPRESSION);
    }

    public static final int BEST_COMPRESSION = Deflater.BEST_COMPRESSION;
    public static final int BEST_SPEED = Deflater.BEST_SPEED;
    public static final int DEFAULT_COMPRESSION = Deflater.DEFAULT_COMPRESSION;
    public static final int NO_COMPRESSION = Deflater.NO_COMPRESSION;
}