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
    static final String DEFAULT_IMAGE="https://www.maulmann.de/images/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-sn47-front.jpg";

    private static final Logger logger = LoggerFactory.getLogger(FileGenerator.class);
    public static final String INDEX_HTML = "index.html";
    public static final String ROOT = "/";

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
            <summary>Which are the rarest Juwan Howard cards in this collection?</summary>
            <p>The collection features some of the most sought-after Juwan Howard cards, including the legendary 1997-98 Metal Universe Precious Metal Gems (PMG) Green, various 1/1 Masterpieces, and rare "Logoman" patches. These cards represent the pinnacle of 90s and modern basketball card collecting.</p>
            </details>
            <details>
            <summary>Does the collection include Juwan Howard's Michigan "Fab Five" era cards?</summary>
            <p>Yes, the collection spans Juwan's entire career, starting with his iconic "Fab Five" years at the University of Michigan. It includes early Classic and Sage autographs, as well as collegiate-themed inserts from modern sets like Panini Flawless and National Treasures.</p>
            </details>
            <details>
            <summary>What is the significance of the 1990s inserts in this collection?</summary>
            <p>The 90s were the golden era of basketball card inserts. This collection showcases Howard's presence in iconic sets like SkyBox Premium Star Rubies, Fleer Brilliants 24-Karat Gold, and E-X2001 Jambalaya, which are highly prized for their innovative designs and extreme scarcity.</p>
            </details>
            <details>
            <summary>Are there cards from Juwan Howard's championship years with the Miami Heat?</summary>
            <p>Absolutely. The collection tracks his transition from a star player to a veteran leader and NBA Champion. You will find rare parallels and autographs from his time with the Miami Heat, including high-end releases from Panini's Gold Standard and Immaculate collections.</p>
            </details>
            <p class="seo-box">
            
            </p>
            """ + SharedTemplates.getFooter(ROOT)+ "</main></body></html>";
            
        addTemplateComponent(generatedFileLocation, templateEnd, true);
    }

    private static String getTemplateBegin(String[] fileNames) {
        String title = "Juwan Howard Basketball Card Collection | 1/1s, PMGs & Rare PC";
        String description = "Explore a massive Juwan Howard private basketball card collection. Featuring rare 90s inserts, PMGs, 1/1s, and autographs from 1994 to today.";
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        sb.append(SharedTemplates.getHead(title, description, ROOT, JUWAN_HOWARD_COLLECTION_HTML, DEFAULT_IMAGE));

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
                "name": "Which are the rarest Juwan Howard cards in this collection?",
                "acceptedAnswer": {
                  "@type": "Answer",
                  "text": "The collection features some of the most sought-after Juwan Howard cards, including the legendary 1997-98 Metal Universe Precious Metal Gems (PMG) Green, various 1/1 Masterpieces, and rare 'Logoman' patches. These cards represent the pinnacle of 90s and modern basketball card collecting."
                }
              },{
                "@type": "Question",
                "name": "Does the collection include Juwan Howard's Michigan 'Fab Five' era cards?",
                "acceptedAnswer": {
                  "@type": "Answer",
                  "text": "Yes, the collection spans Juwan's entire career, starting with his iconic 'Fab Five' years at the University of Michigan. It includes early Classic and Sage autographs, as well as collegiate-themed inserts from modern sets like Panini Flawless and National Treasures."
                }
              },{
                "@type": "Question",
                "name": "What is the significance of the 1990s inserts in this collection?",
                "acceptedAnswer": {
                  "@type": "Answer",
                  "text": "The 90s were the golden era of basketball card inserts. This collection showcases Howard's presence in iconic sets like SkyBox Premium Star Rubies, Fleer Brilliants 24-Karat Gold, and E-X2001 Jambalaya, which are highly prized for their innovative designs and extreme scarcity."
                }
              },{
                "@type": "Question",
                "name": "Are there cards from Juwan Howard's championship years with the Miami Heat?",
                "acceptedAnswer": {
                  "@type": "Answer",
                  "text": "Absolutely. The collection tracks his transition from a star player to a veteran leader and NBA Champion. You will find rare parallels and autographs from his time with the Miami Heat, including high-end releases from Panini's Gold Standard and Immaculate collections."
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
            <p class="seo-box">
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
        internalAnchorList.append("<label for='season-select' style='margin-right: 10px; font-weight: bold;'>Jump to Season:</label>");
        internalAnchorList.append("<select id='season-select' class='modern-button' style='width: auto; min-width: 220px;' onchange=\"if(this.value) window.location.hash = this.value;\">");
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

        String landingContent = """
                <!doctype html>
                <html lang="en">
                <head>
                __HEAD__
                </head>
                <body>
                __TOP_NAV__
                <main class="detail-main">
                    <header class="detail-header">
                        <h1>Maulmann Trading Cards</h1>
                        <p class="sub-title">Welcome to our Private Sports Card Collection</p>
                    </header>

                    <section class="slideshow-container">
                        <div class="mySlides fade">
                            <img src="images/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-sn47-front.jpg" style="width:100%" width="400" height="550" alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red">
                        </div>
                        <div class="mySlides fade">
                            <img src="images/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-sn7-front.jpg" style="width:100%" width="400" height="550" alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green">
                        </div>
                        <div class="mySlides fade">
                            <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Top-Flight-Spectrum-195-sn32-front.jpg" style="width:100%" width="400" height="550" alt="Juwan Howard 1998-99 Upper Deck SPx Finite Top Flight Spectrum">
                        </div>
                        <div class="mySlides fade">
                            <img src="images/1995-96/Juwan-Howard-Washington-Bullets-1995-96-Fleer-E-XL-Base-Set-Blue-88-front.jpg" style="width:100%" width="400" height="550" alt="Juwan Howard 1995-96 Fleer E-XL Base Set Blue">
                        </div>
                        <div class="mySlides fade">
                            <img src="images/1994-95/Juwan-Howard-Washington-Bullets-1994-95-Topps-Topps-Finest-Collegiate-Best-Refractor-259-front.jpg" style="width:100%" width="400" height="550" alt="Juwan Howard 1994-95 Topps Finest Collegiate Best Refractor">
                        </div>
                        <div class="mySlides fade">
                            <img src="images/1994-95/Juwan-Howard-Washington-Bullets-1994-95-Fleer-Ultra-All-Rookies-Base-3-front.jpg" style="width:100%" width="400" height="550" alt="Juwan Howard 1994-95 Fleer Ultra All Rookies">
                        </div>
                    </section>

                    <style>
                        .slideshow-container {
                            max-width: 400px;
                            position: relative;
                            margin: 20px auto;
                            box-shadow: 0 4px 15px rgba(0,0,0,0.3);
                            border-radius: 10px;
                            overflow: hidden;
                        }
                        .mySlides {
                            display: none;
                        }
                        .fade {
                            animation-name: fade;
                            animation-duration: 1.5s;
                        }
                        @keyframes fade {
                            from {opacity: .4}
                            to {opacity: 1}
                        }
                    </style>

                    <script>
                        let slideIndex = 0;
                        showSlides();

                        function showSlides() {
                            let i;
                            let slides = document.getElementsByClassName("mySlides");
                            for (i = 0; i < slides.length; i++) {
                                slides[i].style.display = "none";
                            }
                            slideIndex++;
                            if (slideIndex > slides.length) {slideIndex = 1}
                            slides[slideIndex-1].style.display = "block";
                            setTimeout(showSlides, 3000);
                        }
                    </script>

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

                    <section class="faq-section">
                        <h2>Frequently Asked Questions (FAQ)</h2>
                        <details>
                            <summary>What is the focus of Maulmann Trading Cards?</summary>
                            <p>Maulmann Trading Cards is a premier digital showcase for a private sports card collection. Our primary focus is the legendary <strong>Juwan Howard</strong>, featuring over a thousand unique basketball cards from his career. We also highlight high-end sets like <strong>2012-13 Panini Flawless</strong> and the historic <strong>2008 Upper Deck Exquisite Flawless</strong>, along with rare <strong>Upper Deck Baseball</strong> inscriptions.</p>
                        </details>
                        <details>
                            <summary>How large is the Juwan Howard player collection?</summary>
                            <p>Our Juwan Howard collection is one of the most comprehensive in the hobby, spanning from his early days with the Washington Bullets to his championship years with the Miami Heat. It includes rare 90s inserts, 1/1 masterpieces, and significant parallels that define the "Golden Era" of basketball card collecting.</p>
                        </details>
                        <details>
                            <summary>Why are 90s basketball cards so significant to this collection?</summary>
                            <p>The 1990s represented a revolutionary period in trading cards, introducing iconic technologies like <strong>Refractors</strong>, <strong>Precious Metal Gems (PMGs)</strong>, and <strong>on-card autographs</strong>. Our collection preserves these milestones of hobby history, focusing on the scarcity and aesthetic beauty that modern collectors identify as the pinnacle of the sport.</p>
                        </details>
                        <details>
                            <summary>Can I buy cards from the Maulmann Trading Cards collection?</summary>
                            <p>Currently, the items showcased are part of a private curated collection and are not available for direct sale. However, we are always active in the hobby community and interested in discussing rare finds, especially those on our <strong>Wantlist</strong>. For inquiries, you can reach out to us at <strong>&lt;contact-email-placeholder&gt;</strong>.</p>
                        </details>
                    </section>

                    <script type="application/ld+json">
                        {
                            "@context": "https://schema.org",
                            "@type": "FAQPage",
                            "mainEntity": [
                                {
                                    "@type": "Question",
                                    "name": "What is the focus of Maulmann Trading Cards?",
                                    "acceptedAnswer": {
                                        "@type": "Answer",
                                        "text": "Maulmann Trading Cards showcases a massive private collection of sports cards, focusing on Juwan Howard basketball cards, premium Panini Flawless sets, and historic Upper Deck Exquisite memorabilia."
                                    }
                                },
                                {
                                    "@type": "Question",
                                    "name": "How large is the Juwan Howard player collection?",
                                    "acceptedAnswer": {
                                        "@type": "Answer",
                                        "text": "The Juwan Howard player collection features over a thousand unique cards, including rare 90s inserts, 1/1 masterpieces, and extensive career-spanning parallels."
                                    }
                                },
                                {
                                    "@type": "Question",
                                    "name": "Why are 90s basketball cards so significant to this collection?",
                                    "acceptedAnswer": {
                                        "@type": "Answer",
                                        "text": "The 90s era introduced iconic card technologies such as Refractors and Precious Metal Gems (PMGs), making it a legendary period for collectors that we actively highlight."
                                    }
                                },
                                {
                                    "@type": "Question",
                                    "name": "Can I buy cards from the Maulmann Trading Cards collection?",
                                    "acceptedAnswer": {
                                        "@type": "Answer",
                                        "text": "These are part of a private curated collection and are not for sale. We are, however, interested in hearing from other collectors about rare finds for our wantlist at seraph@gmx.co.uk."
                                    }
                                }
                            ]
                        }
                    </script>
            
                """ + SharedTemplates.getFooter(ROOT) + """
                    </main>
                </body>
                </html>
                """;

        landingContent = landingContent
                .replace("__HEAD__", SharedTemplates.getHead(title, description, ROOT, INDEX_HTML, DEFAULT_IMAGE))
                .replace("__TOP_NAV__", SharedTemplates.getTopNav(ROOT, "index"))
                .replace("<contact-email-placeholder>", "seraph@gmx.co.uk");

        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(targetPath), StandardCharsets.UTF_8);
             BufferedWriter out = new BufferedWriter(osw)) {
            out.write(landingContent);
            out.flush();
        }
    }
}

