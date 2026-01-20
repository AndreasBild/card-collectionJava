package de.maulmann;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class FileGenerator {


    // constants: base paths for input and output
    private static final String pathSource = "content/";
    private static final String pathOutput = "output/";
    private static String generatedFileLocation = pathOutput + "index.html";
    private static String[] nameOfInputFile = getFileNamesFromDirectory();

    private static final Logger logger = LoggerFactory.getLogger(FileGenerator.class);


    // constants for static page parts
    public static final String footer = """
            <h3>Other Collections</h3>
             <ul class="actions-list">
                <li>
                 <a href="index.html" title="Juwan Howard Cards Collection" class="modern-button">
                   Juwan Howard Basketball Cards Collection</a>
                </li>
                <li>
                <a href=Wantlist.html title="Juwan Howard Collection Wantlist" class="modern-button">
                Juwan Howard Basketball Cards Wantlist</a>
                </li>
                <li>
                <a href=Baseball.html title="Upper Deck Baseball Cards" class="modern-button">
                Upper Deck Baseball Cards</a>
                </li>
                <li>
                <a href=Flawless.html title="2008 Upper Deck Flawless Basketball" class="modern-button">
                2008 Upper Deck Flawless Basketball</a></li>
                <li>
                <a href=Panini.html title="2012-13 Panini Flawless Basketball" class="modern-button">
                2012-13 Panini Flawless Basketball</a></li>
                </ul>
                <p>
                    <a class="plain" href="https://jigsaw.w3.org/css-validator/check/referer" title="CSS Validation Label">
                        <img style="border:0;width:88px;height:31px"
                             src="https://jigsaw.w3.org/css-validator/images/vcss"
                             alt="Valid CSS!"
                             title="CSS validator label"/>
                    </a>
                </p>
            """;

    private static final String faqSection = """
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
            """;

    private static final String faqJsonLd = """
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
                                      </script><script type="application/ld+json">
                                      {
                                        "@context": "https://schema.org",
                                        "@type": "CollectionPage",
                                        "name": "Juwan Howard Basketball Trading Card Collection Top 10",
                                        "description": "Private Collection of the TOP 10 Juwan Howard Basketball Trading Cards.",
                                        "mainEntity": {
                                          "@type": "ItemList",
                                          "itemListElement": [
                                            {
                                              "@type": "ListItem",
                                              "position": 1,
                                              "item": {
                                                "@type": "Product",
                                                "name": "1998-99 Fleer Brilliants 24K Gold 14/24 #97TG",
                                                "description": "Limited Parallel",
                                                "brand": { "@type": "Brand", "name": "Fleer Brilliants" },
                                                "offers": {
                                                  "@type": "Offer",
                                                  "availability": "https://schema.org/SoldOut",
                                                  "price": "0",
                                                  "priceCurrency": "EUR",
                                                  "description": "Private Collection - Not for Sale"
                                                }
                                              }
                                            },
                                            {
                                              "@type": "ListItem",
                                              "position": 2,
                                              "item": {
                                                "@type": "Product",
                                                "name": "1997-98 Fleer Metal Universe Precious Metal Gems Red 047/100 #33",
                                                "description": "Limited Parallel",
                                                "brand": { "@type": "Brand", "name": "Fleer Metal Universe" },
                                                "offers": {
                                                  "@type": "Offer",
                                                  "availability": "https://schema.org/SoldOut",
                                                  "price": "0",
                                                  "priceCurrency": "EUR",
                                                  "description": "Private Collection - Not for Sale"
                                                }
                                              }
                                            },
                                            {
                                              "@type": "ListItem",
                                              "position": 3,
                                              "item": {
                                                "@type": "Product",
                                                "name": "1997-98 Fleer Metal Universe Precious Metal Gems Green 007/100 #33",
                                                "description": "Limited Parallel",
                                                "brand": { "@type": "Brand", "name": "Fleer Metal Universe" },
                                                "offers": {
                                                  "@type": "Offer",
                                                  "availability": "https://schema.org/SoldOut",
                                                  "price": "0",
                                                  "priceCurrency": "EUR",
                                                  "description": "Private Collection - Not for Sale"
                                                }
                                              }
                                            },
                                            {
                                              "@type": "ListItem",
                                              "position": 4,
                                              "item": {
                                                "@type": "Product",
                                                "name": "1998-99 Fleer Metal Universe PMG 33/50 #5",
                                                "description": "Limited Parallel",
                                                "brand": { "@type": "Brand", "name": "Fleer Metal Universe" },
                                                "offers": {
                                                  "@type": "Offer",
                                                  "availability": "https://schema.org/SoldOut",
                                                  "price": "0",
                                                  "priceCurrency": "EUR",
                                                  "description": "Private Collection - Not for Sale"
                                                }
                                              }
                                            }
                                          ]
                                        }
                                      }
                                      </script>
            """;
    private static final String templateBegin = """
            <!doctype html>
                <html lang="en">
                    <head>
                        <title>Juwan Howard Basketball Trading Card Collection</title>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <meta http-equiv="X-UA-Compatible" content="IE=edge">
                        <meta name="theme-color" content="#317EFB">
                        <link rel="preload" href="/css/main.css" as="style">
                        <link href="/css/main.css" rel="stylesheet" type="text/css">
                        <link rel="preconnect" href="https://www.googletagmanager.com">
                        <script async src="https://www.googletagmanager.com/gtag/js?id=G-535TKYRZTR"></script>
                            <script>
                                   function loadAnalytics() {
                                     var script = document.createElement('script');
                                     script.src = "https://www.googletagmanager.com/gtag/js?id=G-535TKYRZTR";
                                     script.async = true;
                                     document.head.appendChild(script);
            
                                     window.dataLayer = window.dataLayer || [];
                                     function gtag(){dataLayer.push(arguments);}
                                     gtag('js', new Date());
                                     gtag('config', 'G-535TKYRZTR');
                                   }
                                   window.addEventListener('scroll', loadAnalytics, {once: true});
                                   window.addEventListener('mousemove', loadAnalytics, {once: true});
                                   window.addEventListener('touchstart', loadAnalytics, {once: true});
                            </script>
                        <meta name="description" content="Private Collection of Juwan Howard Basketball Trading Cards. Containing many 1/1 and rare Trading Cards from companies like: Panini, Fleer, Topps and Upper Deck. Including Super rare cards like Precious Metal Gems from the 90s">
                        <meta name="author" content="Mauli Maulmann - Content Creator">
                        <meta name="publisher" content="Mauli Maulmann - Card Collector">
                        <meta name="robots" content="index, follow, max-snippet:-1, max-image-preview:large">
                        <link rel="canonical" href="https://www.maulmann.de/">
                        <meta name="google-site-verification" content="Ev1ZxTPJs2GMFNQ6FyItlCYAKUWscL3jDFS_mVXH6IQ">
                        <link href="/favicon/favicon.ico" rel="icon" sizes="32x32">
                        <link href="/favicon/apple-touch-icon.png" rel="apple-touch-icon">
                        <link rel="apple-touch-icon" sizes="180x180" href="/favicon/apple-touch-icon.png">
                        <link rel="icon" type="image/png" sizes="32x32" href="/favicon/favicon-32x32.png">
                        <link rel="icon" type="image/png" sizes="194x194" href="/favicon/favicon-194x194.png">
                        <link rel="icon" type="image/png" sizes="192x192" href="/favicon/android-chrome-192x192.png">
                        <link rel="icon" type="image/png" sizes="16x16" href="/favicon/favicon-16x16.png">
                        <link rel="manifest" href="/manifest.json">
                        <link rel="mask-icon" href="/favicon/safari-pinned-tab.svg" color="#317EFB">
                        <meta name="apple-mobile-web-app-title" content="Maulmann.de">
                        <meta name="application-name" content="Maulmann.de">
                        <meta name="msapplication-TileColor" content="#317EFB">
            """ + faqJsonLd + """
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
            <h1 id="top" title='Top of the list'>List of Juwan Howard Basketball Trading Cards</h1>
                <p>
                    This page contains my Private Collection of all Juwan Howard Basketball Trading Cards i own.
                    Including many 1/1 and rare Basketball Trading Cards from companies like: Panini,
                    Fleer, Topps, Leaf and Upper Deck. Including super rare cards like Fleer Precious Metal Gems from the 90's
                <p>
            """;

    private static final String tableHead = "<table>";
    private static final String templateEnd = faqSection + footer + "<p>List Created: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()) + "</p></body></html>";


    public static void main(String[] args) throws IOException {

        // These lines ensure that generatedFileLocation and nameOfInputFile are updated
        // if pathSource or pathOutput were changed (e.g., by tests).
        generatedFileLocation = "output/index.html";
        nameOfInputFile = getFileNamesFromDirectory();

        //formatFile();

        // create a new file or use an existing file with the same name
        createTargetFile(generatedFileLocation);
        // add document header as first part of the file content
        addTemplateComponent(templateBegin, false);

        int counterAll = 0;
        for (final String fileName : nameOfInputFile) {
            System.out.println("Filename: " + fileName);
            // iterate over all file names in the given directory
            final String sourceFile = pathSource + fileName + ".html";
            System.out.println("Source: " + sourceFile);
            counterAll = appendFileContent(sourceFile, fileName, counterAll);

        }
        addTemplateComponent(templateEnd, true);
    }

    /**
     * creates an internal anchor in the file
     *
     * @return String an anchor element with all internal anchors
     */
    private static String createAnchorList() {

        final StringBuilder internalAnchorList = new StringBuilder();

        for (final String fileName : nameOfInputFile) {
            internalAnchorList.append(" <a class='modern-button' href=").append('#').append(fileName).append(" title='Juwan Howard Trading Cards from Season: ").append(fileName).append("'>").append(fileName).append("</a> ");
        }
        internalAnchorList.append(" ");


        return internalAnchorList.toString();

    }

    /**
     * Formats a list of files in a given directory by removing whitespaces.
     * Writes the formatted files in a separate directory
     */
    private static void formatFile() {

        final File folder = new File(pathSource);
        final File[] listOfFilesInDirectory = folder.listFiles();

        assert listOfFilesInDirectory != null;
        Arrays.sort(listOfFilesInDirectory);
        for (File aListOfFilesInDirectory : listOfFilesInDirectory) {
            if (aListOfFilesInDirectory.isFile()) {
                final String fileName = aListOfFilesInDirectory.getName();
                if (logger.isInfoEnabled()) {
                    logger.info("Logger FileName: " + fileName);
                }
                //  System.out.println("FileName: " + fileName);
                try {
                    createTargetFile(pathOutput + fileName);
                    formatFileContent(pathSource + fileName, pathOutput + fileName);
                } catch (IOException e) {
                    // Try to get a more specific message if available
                    String message = e.getMessage(); // Using getMessage() directly
                    if (e.getCause() != null && e.getCause().getMessage() != null) {
                        message = e.getCause().getMessage();
                    }
                    System.out.println("Error processing file " + fileName + ": " + message);
                    // e.printStackTrace(); // Optionally print stack trace for more details
                }
            }
        }
    }

    /**
     * @return array of strings containing the names of files/subdirectories in the given base path.
     */
    private static String[] getFileNamesFromDirectory() {
        File directory = new File("./");
        System.out.println("################ AbsolutePath: " + directory.getAbsolutePath());

        final File folder = new File(pathSource);
        final File[] listOfFilesInDirectory = folder.listFiles();


        final List<String> result = new ArrayList<>() {
        };

        assert listOfFilesInDirectory != null;
        Arrays.sort(listOfFilesInDirectory);
        for (File aListOfFilesInDirectory : listOfFilesInDirectory) {
            if (aListOfFilesInDirectory.isFile()) {
                final String substring = aListOfFilesInDirectory.getName().substring(0, aListOfFilesInDirectory.getName().lastIndexOf('.'));
                System.out.println("File in Directory : " + substring);
                result.add(substring);
            } else if (aListOfFilesInDirectory.isDirectory()) {
                System.out.println("Subdirectory in Directory: " + aListOfFilesInDirectory.getName());
            }
        }
        System.out.println("result " + result.size());
        return result.toArray(new String[0]);
    }


    /**
     * Method to create ar re-use a file under the given target location (path in the files System)
     *
     * @throws IOException Java IO Exception
     */
    private static void createTargetFile(String fileName) throws IOException {
        final File myFile = new File(fileName);

        if (myFile.createNewFile()) {
            System.out.println("A new File under: " + fileName + " was created ");
        } else {
            System.out.println("An existing File under: " + fileName + " was replaced ");
        }
    }

    /**
     * Method for adding content to a given file. First, a header with doctype definition is added. In a later step, the rest of the content is appended at the end
     *
     * @param templateBegin  String, which contains the basic file header template
     * @param appendAtTheEnd boolean, that indicated if the provided content shall be added at the end or the beginning of the file
     * @throws IOException, thrown if a file operation did not work out as planned
     */
    private static void addTemplateComponent(String templateBegin, boolean appendAtTheEnd) throws IOException {
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(FileGenerator.generatedFileLocation, appendAtTheEnd), StandardCharsets.UTF_8); BufferedWriter out = new BufferedWriter(osw)) {
            out.append(templateBegin);
            if (!appendAtTheEnd) out.append(createAnchorList());
            out.flush();
        }
    }

    private static int appendFileContent(final String source, final String name, int counterIn) throws IOException {
        final String anchorName = " title='Juwan Howard Trading Cards for Season " + name + "' id='" + name + "'>" + name;
        final StringBuffer result = new StringBuffer("<h2").append(anchorName).append("</h2>").append('\n').append(tableHead);

        try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)); OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(FileGenerator.generatedFileLocation, true), StandardCharsets.UTF_8); BufferedWriter out = new BufferedWriter(osw)) {

            String line;
            int counterAll = counterIn; // keep track of all rows
            int counter = -1; // needs to be -1 because of the table header, we count only the content rows

            while ((line = inputStream.readLine()) != null) {
                //every line, which is a row or column, goes to the resulting file
                if (line.contains("</t") || line.contains("<td") || line.contains("<tr") || line.contains("<th")) {
                    result.append(line.trim());
                    // we simply count the rows, but do not consider the first one as it is the table header
                    if (line.contains("<tr>")) {
                        ++counter;
                    }
                }
            }
            counterAll = counterAll + counter;


            //System.out.println(result);

            final int offset = result.lastIndexOf("</h2>");

            result.replace(offset, offset + 5, " [This Season: " + counter + " | Total: " + counterAll + "]</h2><div> <a href=\"#top\" title='Back to the top of the list' class='s'>top</a></div>");


            out.append(result.append('\n'));
            out.flush();
            return counterAll;

        }
    }

    private static void formatFileContent(final String source, String target) throws IOException {
        final StringBuilder result = new StringBuilder();

        try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)); OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(target, false), StandardCharsets.UTF_8); BufferedWriter out = new BufferedWriter(osw)) {

            String line;
            int counter = -1; // needs to be -1 because of the table header, we count only the content rows

            while ((line = inputStream.readLine()) != null) {
                if (line.isEmpty()) {
                    // Preserve empty lines if necessary, or skip. Original seemed to imply skipping.
                    // For safety, let's assume empty lines in content should not terminate processing.
                    // If the original intent was to stop at the first empty line, this logic might differ.
                    // Based on context (HTML table parsing), skipping empty lines seems more plausible.
                    continue;
                } else if (line.contains("<table>") || line.contains("</table>") || line.contains("<td>") || line.contains("</td>") || line.contains("<tr>") || line.contains("<th>")) {
                    result.append(line.trim()).append('\n');
                } else if (line.contains("</tr>")) {
                    result.append(line.trim()).append('\n');
                    ++counter;
                } else {
                    result.append(line.trim()); // Append trimmed line, but no newline if it's not table structure.
                    // This matches the behavior of the original appendFileContent more closely for non-table lines.
                }
            }

            // The System.out.println(result) was likely for debugging, will omit in restored code unless specified
            // System.out.println(result); 
            final int offset;

            if (result.lastIndexOf("]</h2>") != -1) {
                offset = result.lastIndexOf("]</h2>");
                result.replace(offset, offset + 6, "]</h2>" + "\n");
            } else {
                offset = result.lastIndexOf("</h2>");
                if (offset != -1) { // Ensure "</h2>" was found
                    result.replace(offset, offset + 5, " [Total: " + counter + "]</h2>" + "\n");
                }
            }
            out.append(result.toString()); // Use result.toString()
            out.flush();
        }
    }
}


