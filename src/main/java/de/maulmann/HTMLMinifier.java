package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HTMLMinifier {
    public static void minifyHTML(File inputFile, File outputFile) throws IOException {
        Document document = Jsoup.parse(inputFile, "UTF-8");
        document.outputSettings().prettyPrint(false);
        Files.write(Paths.get(outputFile.getPath()), document.html().getBytes());
    }
}