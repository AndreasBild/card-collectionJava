package de.maulmann;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HtmlToJsonConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        convertOtherBucketsToJson();
    }

    public static void convertOtherBucketsToJson() {
        String[] files = { "Baseball", "Flawless", "Panini", "Wantlist" };
        File jsonDir = new File("content/json");
        if (!jsonDir.exists()) jsonDir.mkdirs();

        for (String name : files) {
            Path htmlPath = Paths.get("content/other", name + ".html");
            if (!Files.exists(htmlPath)) {
                System.out.println("Skipping " + htmlPath + " (file not found)");
                continue;
            }

            try {
                Document doc = Jsoup.parse(htmlPath.toFile(), "UTF-8");
                Elements tables = doc.select("table");
                if (tables.isEmpty()) {
                    System.out.println("No tables found in " + htmlPath);
                    continue;
                }

                List<CardJson> cards = new ArrayList<>();
                for (Element table : tables) {
                    cards.addAll(parseTableToCardJson(table));
                }

                File jsonFile = new File("content/json", name.toLowerCase() + ".json");
                MAPPER.writeValue(jsonFile, cards);
                System.out.println("Successfully generated " + jsonFile.getPath() + " with " + cards.size() + " entries.");
            } catch (IOException e) {
                System.err.println("Error parsing " + htmlPath + ": " + e.getMessage());
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
            if (headers.length > 0) {
                headerRowIndex = i;
                break;
            }
        }

        if (headerRowIndex == -1) return cardList;

        for (int i = headerRowIndex + 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cols = row.children();
            if (cols.isEmpty()) continue;

            CardJson card = new CardJson();
            for (int j = 0; j < cols.size() && j < headers.length; j++) {
                String header = headers[j].toLowerCase();
                String val = cols.get(j).text().trim();
                if (val.isEmpty()) continue;

                switch (header) {
                    case "player":
                        card.player = val;
                        break;
                    case "season":
                        card.season = val;
                        break;
                    case "team":
                        card.team = val;
                        break;
                    case "company":
                    case "manufacturer":
                        card.company = val;
                        break;
                    case "brand":
                        card.brand = val;
                        break;
                    case "theme":
                        card.theme = val;
                        break;
                    case "variant":
                        card.variant = val;
                        break;
                    case "number":
                    case "card number":
                    case "cardnumber":
                        card.cardNumber = val;
                        break;
                    case "serial":
                    case "serialnumber":
                    case "serial/print run":
                        if (val.contains("/")) {
                            String[] parts = val.split("/");
                            card.serialNumber = parts[0].replace("#", "").trim();
                            if ("—".equals(card.serialNumber) || "-".equals(card.serialNumber)) {
                                card.serialNumber = "";
                            }
                            try {
                                card.printRun = Integer.parseInt(parts[1].trim());
                            } catch (Exception ignored) {}
                        } else {
                            card.serialNumber = val.replace("#", "").trim();
                            if ("—".equals(card.serialNumber) || "-".equals(card.serialNumber)) {
                                card.serialNumber = "";
                            }
                        }
                        break;
                    case "print run":
                    case "printrun":
                        try {
                            card.printRun = Integer.parseInt(val.trim());
                        } catch (Exception ignored) {}
                        break;
                    case "grading company":
                    case "grading co.":
                    case "gradingcompany":
                        card.gradingCompany = val;
                        break;
                    case "grade":
                        card.grade = val;
                        break;
                    case "notes":
                        card.notes = val;
                        break;
                    case "collection":
                        card.collection = val;
                        break;
                    case "rookie":
                        card.isRookie = val.equalsIgnoreCase("Yes") || val.equalsIgnoreCase("True");
                        break;
                    case "autograph":
                    case "auto":
                        card.isAutograph = val.equalsIgnoreCase("Yes") || val.equalsIgnoreCase("True");
                        break;
                    case "game used":
                    case "memorabilia":
                    case "patch":
                        card.isPatch = val.equalsIgnoreCase("Yes") || val.equalsIgnoreCase("True");
                        break;
                }
            }
            if (card.player != null || card.brand != null) {
                cardList.add(card);
            }
        }
        return cardList;
    }
}
