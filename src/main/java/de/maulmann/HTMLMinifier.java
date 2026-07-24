package de.maulmann;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HTMLMinifier {

    // --- NEW: IN-MEMORY METHOD ---
    // Use this to pass data directly to your Brotli compressor without touching the disk!
    public static byte[] minifyHTMLToBytes(File inputFile) throws IOException {
        Document document = Jsoup.parse(inputFile, "UTF-8");
        document.outputSettings().prettyPrint(false);
        removeComments(document);

        return document.html().getBytes(StandardCharsets.UTF_8);
    }

    // Recursive helper method to aggressively strip out all HTML comments
    private static void removeComments(Node node) {
        int i = 0;
        while (i < node.childNodes().size()) {
            Node child = node.childNode(i);
            if (child instanceof Comment) {
                child.remove();
            } else {
                removeComments(child);
                i++;
            }
        }
    }
}