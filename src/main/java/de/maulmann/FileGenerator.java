package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class FileGenerator {

    public static final String JUWAN_HOWARD_COLLECTION_HTML = "Juwan-Howard-Collection.html";
    static final String DEFAULT_IMAGE="https://www.maulmann.de/images/1997-98/Juwan-Howard-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-sn7-front.jpg";


    private static final Logger logger = LoggerFactory.getLogger(FileGenerator.class);
    public static final String INDEX_HTML = "index.html";

    // constants: base paths for input and output
    public static String pathSource = "content/";

    public static String pathOutput = "output/";

    public static void main(String[] args) throws IOException {
        String generatedFileLocation = pathOutput + JUWAN_HOWARD_COLLECTION_HTML;
        String[] nameOfInputFile = getFileNamesFromDirectory();

        createLandingPage(pathOutput + INDEX_HTML);

        // create a new file or use an existing file with the same name
        createTargetFile(generatedFileLocation);

        // add document header as first part of the file content
        addTemplateComponent(generatedFileLocation, getTemplateBegin(nameOfInputFile), false);

        int counterAll = 0;
        for (final String fileName : nameOfInputFile) {
            System.out.println("Processing Filename: " + fileName);
            final String sourceFile = pathSource + fileName + ".html";
            counterAll = appendFileContent(generatedFileLocation, sourceFile, fileName, counterAll);
        }

        String templateEnd = """
            <h2 title="Frequently Asked Questions">Frequently Asked Questions</h2>
            <details>
            <summary>What is the focus of this collection?</summary>
            <p>This collection is focused on Juwan Howard basketball trading cards, spanning his entire career from college to the NBA. It includes a wide variety of cards, from base sets to rare inserts, parallels, and autographed cards.</p>
            </details>
            <details>
            <summary>Are any of the cards for sale?</summary>
            <p>Currently, the cards listed on this site are part of a private collection and are not for sale. The purpose of this page is to showcase the collection to fellow enthusiasts.</p>
            </details>
            <details>
            <summary>How often is the collection updated?</summary>
            <p>The collection is updated regularly as new cards are acquired. Check back often to see the latest additions!</p>
            </details>
            """ + SharedTemplates.getFooterNav("/") + SharedTemplates.getFooter() + SharedTemplates.getTimestamp() + "</main></body></html>";
            
        addTemplateComponent(generatedFileLocation, templateEnd, true);
    }

    private static String getTemplateBegin(String[] fileNames) {
        String title = "Juwan Howard Basketball Card Collection | 1/1s, PMGs & Rare PC";
        String description = "Explore a massive Juwan Howard private basketball card collection. Featuring rare 90s inserts, PMGs, 1/1s, and autographs from 1994 to today.";
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        sb.append(SharedTemplates.getHead(title, description, "/" , JUWAN_HOWARD_COLLECTION_HTML, DEFAULT_IMAGE));

        // Additional SEO and metadata specifically for the main collection page
        sb.append("""
            <meta name="keywords" content="Juwan Howard, Basketball Cards, Player Collection, PC, Trading Cards, Panini, Upper Deck, 1/1, PMG, NBA">
            <meta name="google-site-verification" content="Ev1ZxTPJs2GMFNQ6FyItlCYAKUWscL3jDFS_mVXH6IQ">
            """);

        // FAQ JSON-LD can be kept here or moved, it's quite specific.
        sb.append("""
            <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "FAQPage",
              "mainEntity": [{
                "@type": "Question",
                "name": "What is the focus of this collection?",
                "acceptedAnswer": {
                  "@type": "Answer",
                  "text": "This collection is focused on Juwan Howard basketball trading cards, spanning his entire career from college to the NBA. It includes a wide variety of cards, from base sets to rare inserts, parallels, and autographed cards."
                }
              },{
                "@type": "Question",
                "name": "Are any of the cards for sale?",
                "acceptedAnswer": {
                  "@type": "Answer",
                  "text": "Currently, the cards listed on this site are part of a private collection and are not for sale. The purpose of this page is to showcase the collection to fellow enthusiasts."
                }
              },{
                "@type": "Question",
                "name": "How often is the collection updated?",
                "acceptedAnswer": {
                  "@type": "Answer",
                  "text": "The collection is updated regularly as new cards are acquired. Check back often to see the latest additions!"
                }
              }]
            }
            </script>
            """);

        sb.append("""
            <script>
                if ('serviceWorker' in navigator) {
                    window.addEventListener('load', () => {
                        navigator.serviceWorker.register('/serviceWorker.js')
                            .then(registration => {
                                console.log('ServiceWorker registration successful with scope: ', registration.scope);
                            })
                            .catch(error => {
                                console.log('ServiceWorker registration failed: ', error);
                            });
                    });
                }
            </script>
            </head>
            <body>
            """);

        sb.append(SharedTemplates.getTopNav("/", "collection"));

        sb.append("""
            <main class="detail-main">
            <h1 id="top" title='Top of the list'>Juwan Howard Basketball Card - Private Collection</h1>
            <p>
                I wanted to share my dedicated website where I showcase my private basketball card collection. My main focus is my massive Juwan Howard PC. I’ve been building this for years, and the site features hundreds of cards from 1994 to today, including tons of 1/1s, low-numbered parallels, and rare 90s inserts like PMGs.
                You can check out the full Juwan Howard gallery here
            </p>
            """);

        sb.append(createAnchorList(fileNames));

        return sb.toString();
    }

    private static String createAnchorList(String[] fileNames) {
        final StringBuilder internalAnchorList = new StringBuilder();
        internalAnchorList.append("<div style='margin: 20px 0; text-align: left;'>");
        internalAnchorList.append("<label for='season-select'></label>");
        internalAnchorList.append("<select id='season-select' class='modern-button' style='background-color:#4f9e06; width: auto; min-width: 220px; cursor: pointer;' onchange=\"if(this.value) window.location.hash = this.value;\">");
        internalAnchorList.append("<option value=''>-- Select a Season --</option>");

        for (final String fileName : fileNames) {
            internalAnchorList.append("<option value='").append(fileName).append("'>").append(fileName).append("</option>");
        }
        internalAnchorList.append("</select>");
        internalAnchorList.append("</div>");

        return internalAnchorList.toString();
    }

    private static String[] getFileNamesFromDirectory() {
        final File folder = new File(pathSource);
        final File[] listOfFilesInDirectory = folder.listFiles();

        final List<String> result = new ArrayList<>();

        if (listOfFilesInDirectory != null) {
            Arrays.sort(listOfFilesInDirectory);
            for (File file : listOfFilesInDirectory) {
                if (file.isFile() && file.getName().endsWith(".html")) {
                    final String nameWithoutExt = file.getName().substring(0, file.getName().lastIndexOf('.'));
                    result.add(nameWithoutExt);
                }
            }
        }
        return result.toArray(new String[0]);
    }

    private static void createTargetFile(String fileName) throws IOException {
        final File myFile = new File(fileName);
        if (myFile.exists()) {
            myFile.delete();
        }
        if (myFile.createNewFile()) {
            logger.info("A new file under: {} was created", fileName);
        }
    }

    private static void addTemplateComponent(String targetFile, String content, boolean append) throws IOException {
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(targetFile, append), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw)) {
            out.append(content);
            out.flush();
        }
    }

    private static int appendFileContent(String targetFile, String source, String name, int counterIn) throws IOException {
        final String anchorHeader = String.format("<h2 title='Juwan Howard Trading Cards for Season %s' id='%s'>%s", name, name, name);
        final StringBuilder result = new StringBuilder();

        int counter = 0;
        StringBuilder tableContent = new StringBuilder("<table>\n");
        try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8))) {
            String line;
            while ((line = inputStream.readLine()) != null) {
                String trimmed = line.trim();
                if ((trimmed.startsWith("<tr") || trimmed.startsWith("<td") || trimmed.startsWith("<th") || trimmed.startsWith("</t"))
                    && !trimmed.contains("<table")) {
                    tableContent.append(trimmed);
                    if (trimmed.contains("<tr>")) {
                        counter++;
                    }
                }
            }
        }
        tableContent.append("</table>\n");

        // Deduct 1 for header row if it exists
        if (counter > 0) counter--;

        int total = counterIn + counter;

        // Construct header with counts
        result.append(anchorHeader)
              .append(" [This Season: ").append(counter)
              .append(" | Total: ").append(total)
              .append("]</h2>\n");

        // Append table
        result.append(tableContent);

        result.append("<div> <a href=\"#top\" title='Back to the top of the list' class='modern-button'>top</a></div>\n");

        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(targetFile, true), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw)) {
            out.append(result.toString()).append('\n');
            out.flush();
        }
        return total;
    }

    private static void createLandingPage(String targetPath) throws IOException {
        String title = "Maulmann Trading Cards | Juwan Howard & Sports Card Collection";
        String description = "Welcome to Maulmann Trading Cards. Explore our private collection of Juwan Howard basketball cards, rare 90s inserts, and specialty sets like Flawless and Upper Deck Baseball.";

        String landingContent = String.format("""
                <!doctype html>
                <html lang="en">
                <head>
                %s
                </head>
                <body>
                %s
                <main class="detail-main">
                    <header class="detail-header">
                        <h1>Maulmann Trading Cards</h1>
                        <p class="sub-title">Welcome to our Private Sports Card Collection</p>
                    </header>
                
                    <article class="seo-box">
                        <h3>Explore the Collection</h3>
                        <p>Welcome to <strong>Maulmann Trading Cards</strong>, a dedicated space showcasing a lifelong passion for sports card collecting. Our centerpiece is a massive <strong>Juwan Howard Private Collection</strong>, featuring over a thousand unique cards spanning his entire career.</p>
                        <p>Beyond our primary focus, we also feature specialized collections including:</p>
                        <ul>
                            <li><strong>Upper Deck Baseball:</strong> A selection of premium baseball cards.</li>
                            <li><strong>Flawless Basketball:</strong> Showcasing the 2008 and 2012-13 Flawless sets.</li>
                            <li><strong>Wantlist:</strong> Rare cards we are currently searching for to complete our collection.</li>
                        </ul>
                    </article>
                
                    <section style="display: flex; flex-wrap: wrap; gap: 20px; justify-content: center;">
                         <a href="Juwan-Howard-Collection.html" class="modern-button modern-button-footer" style="width: 300px;">Juwan Howard Private Collection</a>
                         <a href="Baseball.html" class="modern-button modern-button-footer" style="width: 300px;">Baseball Collection</a>
                    </section>
                </main>
                
                <footer class="detail-footer">
                    Maulmann Trading Cards &copy; 2026
                </footer>
                </body>
                </html>
                """,
                SharedTemplates.getHead(title, description, "/", INDEX_HTML,DEFAULT_IMAGE),
                SharedTemplates.getTopNav("/", "index"));

        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw)) {
            out.write(landingContent);
            out.flush();
        }
    }
}
