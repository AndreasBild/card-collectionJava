package de.maulmann;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HtmlToJsonConverter {

    private static final Logger log = LoggerFactory.getLogger(HtmlToJsonConverter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        convertOtherBucketsToJson();
    }

    public static void convertOtherBucketsToJson() {
        String[] files = { "Baseball", "Flawless", "Panini", "Wantlist" };
        Path jsonDir = Paths.get("content/json");
        try {
            Files.createDirectories(jsonDir);
        } catch (IOException e) {
            log.error("Could not create directory {}: {}", jsonDir, e.getMessage());
            return;
        }

        for (String name : files) {
            Path htmlPath = Paths.get("content/other", name + ".html");
            if (!Files.exists(htmlPath)) {
                log.info("Skipping {} (file not found)", htmlPath);
                continue;
            }

            try {
                Document doc = Jsoup.parse(htmlPath.toFile(), "UTF-8");
                Elements tables = doc.select("table");
                if (tables.isEmpty()) {
                    log.info("No tables found in {}", htmlPath);
                    continue;
                }

                List<CardJson> cards = new ArrayList<>();
                for (Element table : tables) {
                    cards.addAll(parseTableToCardJson(table));
                }

                File jsonFile = new File("content/json", name.toLowerCase() + ".json");
                MAPPER.writeValue(jsonFile, cards);
                log.info("Successfully generated {} with {} entries.", jsonFile.getPath(), cards.size());
            } catch (IOException e) {
                log.error("Error parsing {}: {}", htmlPath, e.getMessage());
            }
        }
    }

    public static List<CardJson> parseTableToCardJson(Element table) {
        List<CardJson> cardList = new ArrayList<>();
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return cardList;

        int headerRowIndex = -1;
        String[] headers = null;

        for (int i = 0; i < rows.size(); i++) {
            Elements cells = rows.get(i).children();
            if (cells.isEmpty()) continue;
            headers = new String[cells.size()];
            for (int j = 0; j < cells.size(); j++) {
                headers[j] = cells.get(j).text().trim();
            }
            headerRowIndex = i;
            break;
        }

        if (headerRowIndex == -1) return cardList;

        for (int i = headerRowIndex + 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cols = row.children();
            if (cols.isEmpty()) continue;

            CardJson.Builder cardBuilder = CardJson.builder();
            for (int j = 0; j < cols.size() && j < headers.length; j++) {
                String header = headers[j].toLowerCase();
                String val = cols.get(j).text().trim();
                if (val.isEmpty()) continue;

                switch (header) {
                    case "player":
                        cardBuilder.player(val);
                        break;
                    case "season":
                        cardBuilder.season(val);
                        break;
                    case "team":
                        cardBuilder.team(val);
                        break;
                    case "company":
                    case "manufacturer":
                        cardBuilder.company(val);
                        break;
                    case "brand":
                        cardBuilder.brand(val);
                        break;
                    case "theme":
                        cardBuilder.theme(val);
                        break;
                    case "variant":
                        cardBuilder.variant(val);
                        break;
                    case "number":
                    case "card number":
                    case "cardnumber":
                        cardBuilder.cardNumber(val);
                        break;
                    case "serial":
                    case "serialnumber":
                    case "serial/print run":
                        if (val.contains("/")) {
                            String[] parts = val.split("/");
                            String sn = parts[0].replace("#", "").trim();
                            if ("—".equals(sn) || "-".equals(sn)) {
                                sn = "";
                            }
                            cardBuilder.serialNumber(sn);
                            try {
                                cardBuilder.printRun(Integer.parseInt(parts[1].trim()));
                            } catch (Exception ignored) {}
                        } else {
                            String sn = val.replace("#", "").trim();
                            if ("—".equals(sn) || "-".equals(sn)) {
                                sn = "";
                            }
                            cardBuilder.serialNumber(sn);
                        }
                        break;
                    case "print run":
                    case "printrun":
                        try {
                            cardBuilder.printRun(Integer.parseInt(val.trim()));
                        } catch (Exception ignored) {}
                        break;
                    case "grading company":
                    case "grading co.":
                    case "gradingcompany":
                        cardBuilder.gradingCompany(val);
                        break;
                    case "grade":
                        cardBuilder.grade(val);
                        break;
                    case "notes":
                        cardBuilder.notes(val);
                        break;
                    case "collection":
                        cardBuilder.collection(val);
                        break;
                    case "rookie":
                        cardBuilder.isRookie(val.equalsIgnoreCase("Yes") || val.equalsIgnoreCase("True"));
                        break;
                    case "autograph":
                    case "auto":
                        cardBuilder.isAutograph(val.equalsIgnoreCase("Yes") || val.equalsIgnoreCase("True"));
                        break;
                    case "game used":
                    case "memorabilia":
                    case "patch":
                        cardBuilder.isPatch(val.equalsIgnoreCase("Yes") || val.equalsIgnoreCase("True"));
                        break;
                }
            }
            CardJson card = cardBuilder.build();
            if (card.player() != null || card.brand() != null) {
                cardList.add(card);
            }
        }
        return cardList;
    }
}
