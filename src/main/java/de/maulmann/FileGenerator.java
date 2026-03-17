package de.maulmann;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileGenerator {

    // Standard-Bild für Social Media Sharing (Open Graph) - Auf WebP aktualisiert!
    static final String DEFAULT_IMAGE = "https://www.maulmann.de/images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-sn47-front.webp";

    public static void main(String[] args) {
        System.out.println("-> Generiere Startseite (index.html)...");
        createLandingPage();

        // ==========================================================
        // HIER KOMMT DEIN BESTEHENDER CODE FÜR DIE UNTERSEITEN REIN:
        // z.B. das Einlesen deiner CSV/JSON/Datenbank und der Aufruf
        // von CardPageGenerator.createSubPage(card) für jede Karte.
        // ==========================================================

        System.out.println("-> HTML-Generierung abgeschlossen.");
    }

    public static void createLandingPage() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html>\n");
            sb.append("<html lang=\"en\">\n");

            // --- KOPFBEREICH (Ohne FontAwesome!) ---
            // Nutze deine SharedTemplates, um den Head zu bauen.
            // Wichtig: In der SharedTemplates.getHead() Methode solltest du den
            // <link rel="stylesheet" href="...font-awesome.min.css"> Eintrag komplett löschen!
            String headHtml = SharedTemplates.getHead(
                    "Maulmann Trading Cards | Juwan Howard & Sports Card Collection",
                    "Private collection of Juwan Howard basketball cards, featuring 1/1s, PMGs, and rare 90s inserts.",
                    "",
                    "index.html",
                    DEFAULT_IMAGE
            );
            sb.append(headHtml).append("\n");

            sb.append("<body>\n");

            // --- NAVIGATION ---
            String topnav = SharedTemplates.loadResource("/templates/topnav.html");
            sb.append(topnav.replace("{{ROOT}}", "")).append("\n");

            // --- HAUPTINHALT ---
            sb.append("    <div class=\"detail-main\">\n");
            sb.append("        <div class=\"detail-header\">\n");
            sb.append("            <h1>Maulmann Trading Cards</h1>\n");
            sb.append("            <p class=\"sub-title\">Welcome to our Private Sports Card Collection</p>\n");
            sb.append("        </div>\n");

            // --- NEUES MODERNES KARTEN-KARUSSELL (Kein JavaScript, kein CLS!) ---
            sb.append("        <style>\n");
            sb.append("            .card-carousel {\n");
            sb.append("                display: flex;\n");
            sb.append("                overflow-x: auto;\n");
            sb.append("                scroll-snap-type: x mandatory;\n");
            sb.append("                gap: 20px;\n");
            sb.append("                padding: 20px 0;\n");
            sb.append("                margin-bottom: 30px;\n");
            sb.append("                scrollbar-width: none; /* Firefox */\n");
            sb.append("                -webkit-overflow-scrolling: touch;\n");
            sb.append("            }\n");
            sb.append("            .card-carousel::-webkit-scrollbar {\n");
            sb.append("                display: none; /* Chrome/Safari */\n");
            sb.append("            }\n");
            sb.append("            .carousel-item {\n");
            sb.append("                flex: 0 0 auto;\n");
            sb.append("                width: 250px;\n");
            sb.append("                scroll-snap-align: center;\n");
            sb.append("                transition: transform 0.3s ease;\n");
            sb.append("            }\n");
            sb.append("            .carousel-item:hover {\n");
            sb.append("                transform: translateY(-8px);\n");
            sb.append("            }\n");
            sb.append("            .carousel-item img {\n");
            sb.append("                width: 100%;\n");
            sb.append("                height: auto;\n");
            sb.append("                aspect-ratio: 400 / 550;\n");
            sb.append("                border-radius: 8px;\n");
            sb.append("                box-shadow: 0 6px 15px rgba(0,0,0,0.2);\n");
            sb.append("                object-fit: contain;\n");
            sb.append("                background-color: #fff;\n");
            sb.append("                display: block;\n");
            sb.append("            }\n");
            sb.append("            @media (max-width: 600px) {\n");
            sb.append("                .carousel-item {\n");
            sb.append("                    width: 200px;\n");
            sb.append("                }\n");
            sb.append("            }\n");
            sb.append("        </style>\n");

            sb.append("        <section class=\"card-carousel\">\n");

            // Karte 1 (LCP: fetchpriority="high", NICHT lazy geladen!)
            sb.append("            <div class=\"carousel-item\">\n");
            sb.append("                <img src=\"images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-sn47-front.webp\" fetchpriority=\"high\" width=\"400\" height=\"550\" alt=\"Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red\">\n");
            sb.append("            </div>\n");

            // Karte 2 (Lazy)
            sb.append("            <div class=\"carousel-item\">\n");
            sb.append("                <img src=\"images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-sn7-front.webp\" loading=\"lazy\" width=\"400\" height=\"550\" alt=\"Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green\">\n");
            sb.append("            </div>\n");

            // Karte 3 (Lazy)
            sb.append("            <div class=\"carousel-item\">\n");
            sb.append("                <img src=\"images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Upper-Deck-UD3-Season-Ticket-Autograph-Base-JH-front.webp\" loading=\"lazy\" width=\"400\" height=\"550\" alt=\"Juwan Howard 1997-98 UD3 Season Ticket Autograph\">\n");
            sb.append("            </div>\n");

            // Karte 4 (Lazy)
            sb.append("            <div class=\"carousel-item\">\n");
            sb.append("                <img src=\"images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Base-Set-Spectrum-76-sn291-front.webp\" loading=\"lazy\" width=\"400\" height=\"550\" alt=\"Juwan Howard 1998-99 SPx Finite Base Spectrum\">\n");
            sb.append("            </div>\n");

            // Karte 5 (Lazy)
            sb.append("            <div class=\"carousel-item\">\n");
            sb.append("                <img src=\"images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Star-Power-Spectrum-92-sn223-front.webp\" loading=\"lazy\" width=\"400\" height=\"550\" alt=\"JJuwan Howard 1998-99 SPx Finite Star Power Spectrum\">\n");
            sb.append("            </div>\n");

            // Karte 6 (Lazy)
            sb.append("            <div class=\"carousel-item\">\n");
            sb.append("                <img src=\"images/1994-95/Juwan-Howard-Washington-Bullets-1994-95-Fleer-Ultra-All-Rookies-Base-3-front.webp\" loading=\"lazy\" width=\"400\" height=\"550\" alt=\"Juwan Howard 1994-95 Fleer Ultra All Rookies\">\n");
            sb.append("            </div>\n");

            sb.append("        </section>\n");

            // --- TEXT CONTENT ---
            sb.append("        <div class=\"seo-box\">\n");
            sb.append("            <h2>Explore the Collection</h2>\n");
            sb.append("            <p>Welcome to Maulmann Trading Cards, a dedicated space showcasing a lifelong passion for sports card collecting. Our centerpiece is a massive Juwan Howard Private Collection, featuring over a thousand unique cards spanning his entire career.</p>\n");
            sb.append("            <p>Beyond our primary focus, we also feature specialized collections including:</p>\n");
            sb.append("            <ul>\n");
            sb.append("                <li><strong>Upper Deck Baseball:</strong> A selection of premium baseball cards.</li>\n");
            sb.append("                <li><strong>Flawless Basketball:</strong> Showcasing the 2008 and 2012-13 Flawless sets.</li>\n");
            sb.append("                <li><strong>Wantlist:</strong> Rare cards we are currently searching for to complete our collection.</li>\n");
            sb.append("            </ul>\n");
            sb.append("        </div>\n");

            // --- FOOTER ---
            String footer = SharedTemplates.loadResource("/templates/footer.html");
            sb.append(footer.replace("{{ROOT}}", "")).append("\n");

            sb.append("    </div>\n"); // End detail-main
            sb.append("</body>\n");
            sb.append("</html>\n");

            // --- SCHREIBEN DER INDEX.HTML ---
            File outputDir = new File("output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            File file = new File(outputDir, "index.html");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(sb.toString());
            }

        } catch (IOException e) {
            System.err.println("Fehler beim Erstellen der Startseite: " + e.getMessage());
            e.printStackTrace();
        }
    }
}