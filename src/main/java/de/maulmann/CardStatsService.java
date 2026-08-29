package de.maulmann;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Service for calculating trading card collection statistics,
 * filtering duplicates, and identifying rare masterpiece cards.
 */
public class CardStatsService {

    public static String formatSerialAndPrintRun(String serialNum, Integer printRun, String fallback) {
        boolean hasSerial = serialNum != null && !serialNum.trim().isEmpty() && !serialNum.equals("0");
        boolean hasPrintRun = printRun != null && printRun > 0;

        if (hasSerial && hasPrintRun) {
            return serialNum.contains("/") ? serialNum : serialNum + "/" + printRun;
        } else if (hasSerial) {
            return serialNum.startsWith("#") ? serialNum : "#" + serialNum;
        } else if (hasPrintRun) {
            return "/" + printRun;
        } else if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback;
        }
        return "Parallel";
    }

    public static List<CardJson> filterDuplicateJsonCards(List<CardJson> rawCards) {
        if (rawCards == null) return List.of();
        List<CardJson> filtered = new ArrayList<>();
        Set<String> seenFingerprints = new HashSet<>();

        for (CardJson c : rawCards) {
            String season = c.season() != null ? c.season() : "";
            String company = c.company() != null ? c.company() : "";
            String brand = c.brand() != null ? c.brand() : "";
            String theme = c.theme() != null ? c.theme() : "";
            String variant = c.variant() != null ? c.variant() : "";
            String number = c.cardNumber() != null ? c.cardNumber() : "";
            String gradingCo = c.gradingCompany() != null ? c.gradingCompany() : "";
            String grade = c.grade() != null ? c.grade() : "";

            String fingerprint = (season + "|" + company + "|" + brand + "|" + theme + "|" +
                    variant + "|" + number + "|" + gradingCo + "|" + grade).toLowerCase();

            String serial = c.serialNumber();
            boolean hasSerial = serial != null && !serial.trim().isEmpty() && !serial.trim().equals("0");

            if (seenFingerprints.contains(fingerprint)) {
                if (hasSerial) {
                    filtered.add(c);
                }
            } else {
                seenFingerprints.add(fingerprint);
                filtered.add(c);
            }
        }
        return filtered;
    }

    public static Map<String, Object> computeCollectionStats(List<CardJson> jsonCards) {
        Map<String, Object> stats = new HashMap<>();
        if (jsonCards == null || jsonCards.isEmpty()) {
            stats.put("totalCards", "0");
            stats.put("rawTotalCards", 0);
            stats.put("count1of1", 0);
            stats.put("pct1of1", 0);
            stats.put("countUltraSp", 0);
            stats.put("pctUltraSp", 0);
            stats.put("countSerialized", 0);
            stats.put("pctSerialized", 0);
            stats.put("countAutographs", 0);
            stats.put("pctAutographs", 0);
            stats.put("countPatches", 0);
            stats.put("pctPatches", 0);
            stats.put("countRookies", 0);
            stats.put("pctRookies", 0);
            stats.put("countGradedTotal", 0);
            stats.put("pctGradedTotal", 0);
            stats.put("countGemMint", 0);
            stats.put("pctGemMint", 0);
            return stats;
        }

        int count1of1 = 0;
        int countUltraSp = 0;
        int countSerialized = 0;
        int countAutographs = 0;
        int countPatches = 0;
        int countRookies = 0;
        int countGradedTotal = 0;
        int countGemMint = 0;
        int countJerseyNumbers = 0;
        double totalEstimatedVal = 0.0;
        int countPriced = 0;
        double totalGradedVal = 0.0;
        int countGradedPriced = 0;

        // Era breakdown counters
        int eraBullets = 0;
        int eraWizards = 0;
        int eraMavsRockets = 0;
        int eraHeat = 0;
        int eraPanini = 0;
        int eraOther = 0;

        // Manufacturer distribution counters
        Map<String, Integer> mfgDist = new HashMap<>();

        for (CardJson c : jsonCards) {
            Integer pr = c.printRun();
            String sn = c.serialNumber() != null ? c.serialNumber().trim() : "";
            String v = c.variant() != null ? c.variant().trim() : "";
            String t = c.theme() != null ? c.theme().trim() : "";
            String s = c.season() != null ? c.season().trim() : "";
            String comp = c.company() != null ? c.company().trim() : "";

            boolean isOneOfOne = (pr != null && pr == 1) ||
                    "1/1".equalsIgnoreCase(sn) || "1/1".equalsIgnoreCase(v) || "1/1".equalsIgnoreCase(t) ||
                    "1 of 1".equalsIgnoreCase(sn) || "1 of 1".equalsIgnoreCase(v) || "1 of 1".equalsIgnoreCase(t);
            if (isOneOfOne) count1of1++;

            if (pr != null && pr > 0 && pr <= 10) countUltraSp++;

            boolean isSerialized100 = (pr != null && pr > 0 && pr <= 100) ||
                    ((pr == null || pr == 0) && !sn.isEmpty() && !sn.equals("0"));
            if (isSerialized100) countSerialized++;

            if (c.isAutograph()) countAutographs++;
            if (c.isPatch()) countPatches++;
            if (c.isRookie()) countRookies++;

            if (!sn.isEmpty()) {
                String clean = sn.replace("#", "").replaceAll("^0+", "");
                if (clean.equals("5")) countJerseyNumbers++;
            }

            Double val = c.estimatedValue() != null && c.estimatedValue() > 0.0 ? c.estimatedValue() :
                    (c.lastSoldPrice() != null && c.lastSoldPrice() > 0.0 ? c.lastSoldPrice() :
                    (c.purchasePrice() != null && c.purchasePrice() > 0.0 ? c.purchasePrice() : null));

            if (val != null) {
                totalEstimatedVal += val;
                countPriced++;
            }

            String gCo = c.gradingCompany();
            String g = c.grade();
            boolean isGraded = (gCo != null && !gCo.trim().isEmpty() && !gCo.trim().equalsIgnoreCase("No")) ||
                    (g != null && !g.trim().isEmpty() && !g.trim().equalsIgnoreCase("No") && !g.trim().equals("-"));
            if (isGraded) {
                countGradedTotal++;
                if (g != null && (g.contains("10") || g.contains("9.5"))) {
                    countGemMint++;
                }
                if (val != null) {
                    totalGradedVal += val;
                    countGradedPriced++;
                }
            }

            // Era categorisation
            if (s.contains("1994") || s.contains("1995") || s.contains("1996")) {
                eraBullets++;
            } else if (s.contains("1997") || s.contains("1998") || s.contains("1999") || s.contains("2000")) {
                eraWizards++;
            } else if (s.contains("2001") || s.contains("2002") || s.contains("2003") || s.contains("2004") || s.contains("2005") || s.contains("2006") || s.contains("2007")) {
                eraMavsRockets++;
            } else if (s.contains("2010") || s.contains("2011") || s.contains("2012") || s.contains("2013")) {
                eraHeat++;
            } else if (s.contains("2014") || s.contains("2015") || s.contains("2016") || s.contains("2017") || s.contains("2018") || s.contains("2019") || s.contains("202") || comp.equalsIgnoreCase("Panini")) {
                eraPanini++;
            } else {
                eraOther++;
            }

            // Manufacturer categorization
            String mfgKey = "Other";
            String compLower = comp.toLowerCase();
            if (compLower.contains("fleer") || compLower.contains("skybox")) mfgKey = "Fleer / SkyBox";
            else if (compLower.contains("topps") || compLower.contains("bowman")) mfgKey = "Topps / Bowman";
            else if (compLower.contains("panini") || compLower.contains("donruss")) mfgKey = "Panini / Donruss";
            else if (compLower.contains("upper deck") || compLower.contains("ud")) mfgKey = "Upper Deck";
            else if (compLower.contains("press pass") || compLower.contains("score board") || compLower.contains("classic")) mfgKey = "Draft / College";
            mfgDist.put(mfgKey, mfgDist.getOrDefault(mfgKey, 0) + 1);
        }

        int totalCardCount = jsonCards.size();
        stats.put("totalCards", String.format(Locale.US, "%,d", totalCardCount));
        stats.put("rawTotalCards", totalCardCount);

        stats.put("count1of1", count1of1);
        stats.put("pct1of1", count1of1 > 0 ? Math.max(5, (int) Math.round((count1of1 * 100.0) / totalCardCount)) : 0);

        stats.put("countUltraSp", countUltraSp);
        stats.put("pctUltraSp", countUltraSp > 0 ? Math.max(5, (int) Math.round((countUltraSp * 100.0) / totalCardCount)) : 0);

        stats.put("countSerialized", countSerialized);
        stats.put("pctSerialized", countSerialized > 0 ? Math.max(5, (int) Math.round((countSerialized * 100.0) / totalCardCount)) : 0);

        stats.put("countAutographs", countAutographs);
        stats.put("pctAutographs", countAutographs > 0 ? Math.max(5, (int) Math.round((countAutographs * 100.0) / totalCardCount)) : 0);

        stats.put("countPatches", countPatches);
        stats.put("pctPatches", countPatches > 0 ? Math.max(5, (int) Math.round((countPatches * 100.0) / totalCardCount)) : 0);

        stats.put("countRookies", countRookies);
        stats.put("pctRookies", countRookies > 0 ? Math.max(5, (int) Math.round((countRookies * 100.0) / totalCardCount)) : 0);

        stats.put("countGradedTotal", countGradedTotal);
        stats.put("pctGradedTotal", countGradedTotal > 0 ? Math.max(5, (int) Math.round((countGradedTotal * 100.0) / totalCardCount)) : 0);

        stats.put("countGemMint", countGemMint);
        stats.put("pctGemMint", countGradedTotal > 0 ? Math.max(5, (int) Math.round((countGemMint * 100.0) / countGradedTotal)) : 0);

        stats.put("countJerseyNumbers", countJerseyNumbers);
        stats.put("countPriced", countPriced);
        stats.put("totalEstimatedValue", totalEstimatedVal);
        stats.put("formattedEstimatedValue", String.format(Locale.US, "$%,.0f", totalEstimatedVal));

        double avgCardVal = countPriced > 0 ? totalEstimatedVal / countPriced : 0.0;
        stats.put("avgCardValue", avgCardVal);
        stats.put("formattedAvgCardValue", String.format(Locale.US, "$%.2f", avgCardVal));

        double avgGradedVal = countGradedPriced > 0 ? totalGradedVal / countGradedPriced : 0.0;
        stats.put("avgGradedValue", avgGradedVal);
        stats.put("formattedAvgGradedValue", String.format(Locale.US, "$%.2f", avgGradedVal));
        stats.put("totalGradedValue", totalGradedVal);
        stats.put("formattedTotalGradedValue", String.format(Locale.US, "$%,.0f", totalGradedVal));

        int marketCoveragePct = totalCardCount > 0 ? (int) Math.round((countPriced * 100.0) / totalCardCount) : 0;
        stats.put("marketCoveragePct", marketCoveragePct);

        // Era breakdown
        Map<String, Integer> eraMap = new HashMap<>();
        eraMap.put("bullets", eraBullets);
        eraMap.put("wizards", eraWizards);
        eraMap.put("mavsRockets", eraMavsRockets);
        eraMap.put("heat", eraHeat);
        eraMap.put("panini", eraPanini);
        eraMap.put("other", eraOther);
        stats.put("eraDistribution", eraMap);
        stats.put("manufacturerDistribution", mfgDist);

        return stats;
    }

    public static boolean isOneOfOneMasterpiece(CardJson c) {
        if (c == null) return false;
        String var = c.variant() != null ? c.variant().toLowerCase() : "";
        String theme = c.theme() != null ? c.theme().toLowerCase() : "";
        String brand = c.brand() != null ? c.brand().toLowerCase() : "";
        String sn = c.serialNumber() != null ? c.serialNumber().trim() : "";

        // Exclude Printing Plates & Proofs
        if (var.contains("plate") || theme.contains("plate") || brand.contains("plate") ||
            var.contains("proof") || theme.contains("proof") || brand.contains("proof")) {
            return false;
        }

        if (c.printRun() != null && c.printRun() == 1) {
            return true;
        }
        if (sn.equals("1/1") || sn.equalsIgnoreCase("1 of 1")) {
            return true;
        }
        return var.contains("1/1") || var.contains("1 of 1");
    }
}
