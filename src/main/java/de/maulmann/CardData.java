package de.maulmann;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rich domain model for a single Trading Card page and entity representation.
 */
public class CardData {

    private static final Pattern PATTERN_CLEAN_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9\\-_]");
    private static final Pattern PATTERN_CLEAN_FILENAME_HYPHENS = Pattern.compile("-+");
    private static final Pattern PATTERN_CLEAN_FILENAME_EDGES = Pattern.compile("^-|-$");
    private static final Pattern PATTERN_CLEAN_PLAYER_QUOTES = Pattern.compile("[\"“„”«»'].*?[\"“„”«»']");
    private static final Pattern PATTERN_SPACES = Pattern.compile("\\s+");

    public final Map<String, String> attributes;
    public String stableId;
    public String filenameBase;
    public String filename;
    public String seasonFolder;
    public String fullRelativePath;

    public CardData(CardJson c, String uniqueId) {
        this.attributes = new HashMap<>();
        if (c.player() != null) {
            this.attributes.put("Player", c.player());
        } else if (c.collection() != null && !c.collection().trim().isEmpty()) {
            this.attributes.put("Player", c.collection());
        }
        if (c.season() != null) this.attributes.put("Season", c.season());
        if (c.team() != null) this.attributes.put("Team", c.team());
        if (c.company() != null) this.attributes.put("Company", c.company());
        if (c.brand() != null) this.attributes.put("Brand", c.brand());
        if (c.theme() != null) this.attributes.put("Theme", c.theme());
        if (c.variant() != null) this.attributes.put("Variant", c.variant());
        if (c.cardNumber() != null) this.attributes.put("Number", c.cardNumber());
        if (c.serialNumber() != null) this.attributes.put("Serial", c.serialNumber());
        if (c.printRun() != null) this.attributes.put("Print Run", String.valueOf(c.printRun()));
        if (c.gradingCompany() != null) this.attributes.put("Grading Co.", c.gradingCompany());
        if (c.grade() != null) this.attributes.put("Grade", c.grade());
        if (c.notes() != null) this.attributes.put("Notes", c.notes());

        String autoVal = c.isAutograph() ? "Yes" : "No";
        this.attributes.put("Autograph", autoVal);
        this.attributes.put("Auto", autoVal);
        this.attributes.put("isAutograph", autoVal);

        String memVal = c.isPatch() ? "Yes" : "No";
        this.attributes.put("Memorabilia", memVal);
        this.attributes.put("Game Used", memVal);
        this.attributes.put("Mem / Patch", memVal);

        String rkVal = c.isRookie() ? "Yes" : "No";
        this.attributes.put("Rookie", rkVal);

        String currentTeam = this.attributes.get("Team");
        if (!isValid(currentTeam)) {
            String player = this.attributes.get("Player");
            if (player != null && player.startsWith("Juwan Howard")) {
                String calculatedTeam = getTeamBySeason(this.attributes.get("Season"));
                this.attributes.put("Team", calculatedTeam);
            }
        }

        if (uniqueId != null && !uniqueId.isEmpty()) {
            this.stableId = uniqueId;
        } else {
            this.stableId = generateStableId(this.attributes);
        }

        calculatePaths(this.stableId);
    }

    private void calculatePaths(String uniqueId) {
        List<String> filenameTokens = new ArrayList<>();
        String pStr = attributes.get("Player");
        if (pStr != null && pStr.contains(",")) pStr = pStr.split(",")[0].trim();
        addIfPresent(filenameTokens, pStr);

        String tStr = attributes.get("Team");
        if (tStr != null && tStr.contains(",")) tStr = tStr.split(",")[0].trim();
        addIfPresent(filenameTokens, tStr);
        addIfPresent(filenameTokens, attributes.get("Season"));
        addIfPresent(filenameTokens, attributes.get("Company"));
        addIfPresent(filenameTokens, attributes.get("Brand"));
        addIfPresent(filenameTokens, attributes.get("Theme"));
        addIfPresent(filenameTokens, attributes.get("Variant"));
        addIfPresent(filenameTokens, attributes.get("Number"));

        String serial = attributes.get("Serial");
        if (!isValid(serial)) {
            serial = attributes.get("Serial/Print Run");
        }
        if (isValid(serial) && !serial.equals("0")) {
            String cleanSerial = serial.replace("#", "").replace("/", "-");
            filenameTokens.add("sn" + cleanSerial);
        }

        String gradingCo = attributes.get("Grading Co.");
        if (isValid(gradingCo)) filenameTokens.add(gradingCo);

        String grade = attributes.get("Grade");
        if (isValid(grade)) filenameTokens.add(grade);

        this.filenameBase = cleanFilename(String.join("-", filenameTokens)) + "-" + uniqueId;
        this.filename = this.filenameBase + ".html";
        String seasonRaw = attributes.get("Season");
        this.seasonFolder = isValid(seasonRaw) ? cleanFilename(seasonRaw) : "Unknown_Season";
        this.fullRelativePath = "cards/" + this.seasonFolder + "/" + this.filename;
    }

    public String get(String key) {
        return attributes.getOrDefault(key, "");
    }

    public boolean has(String key) {
        String val = attributes.get(key);
        return isValid(val);
    }

    public static boolean isValid(String val) {
        return val != null && !val.trim().isEmpty() && !val.trim().equalsIgnoreCase("null") && !val.trim().equals("—") && !val.trim().equals("-");
    }

    public static void addIfPresent(List<String> list, String val) {
        if (isValid(val)) {
            list.add(val.trim());
        }
    }

    public static String cleanFilename(String name) {
        if (name == null) return "card";
        String s = PATTERN_CLEAN_PLAYER_QUOTES.matcher(name).replaceAll("");
        s = s.replace("/", "-").replace("&", "and").replace(" ", "-").replace(".", "-").replace("#", "");
        s = PATTERN_CLEAN_FILENAME_CHARS.matcher(s).replaceAll("");
        s = PATTERN_CLEAN_FILENAME_HYPHENS.matcher(s).replaceAll("-");
        s = PATTERN_CLEAN_FILENAME_EDGES.matcher(s).replaceAll("");
        return s.isEmpty() ? "card" : s;
    }

    public static String cleanPlayerName(String player) {
        if (player == null) return "Juwan Howard";
        String p = PATTERN_CLEAN_PLAYER_QUOTES.matcher(player).replaceAll("");
        return PATTERN_SPACES.matcher(p).replaceAll(" ").trim();
    }

    public static String getTeamBySeason(String season) {
        if (season == null) return "Washington Bullets";
        String s = season.trim().toLowerCase();
        if (s.contains("college")) return "Michigan Wolverines";
        if (s.contains("1994-95") || s.contains("1995-96") || s.contains("1996-97")) return "Washington Bullets";
        if (s.contains("1997-98") || s.contains("1998-99") || s.contains("1999-00") || s.contains("2000-01")) return "Washington Wizards";
        if (s.contains("2001-02") || s.contains("2002-03")) return "Dallas Mavericks";
        if (s.contains("2003-04")) return "Orlando Magic";
        if (s.contains("2004-05") || s.contains("2005-06") || s.contains("2006-07")) return "Houston Rockets";
        if (s.contains("2007-08")) return "Dallas Mavericks";
        if (s.contains("2008-09")) return "Charlotte Bobcats";
        if (s.contains("2009-10")) return "Portland Trail Blazers";
        if (s.contains("2010-11") || s.contains("2011-12") || s.contains("2012-13")) return "Miami Heat";
        return "Washington Wizards";
    }

    public static String generateStableId(Map<String, String> attributes) {
        String[] relevantKeys = {
                "Player", "Team", "Season", "Company", "Brand",
                "Theme", "Variant", "Number", "Serial", "Print Run",
                "Serial/Print Run", "Grade"
        };

        StringBuilder sb = new StringBuilder();
        for (String key : relevantKeys) {
            String val = attributes.getOrDefault(key, "").trim();
            if (key.equals("Player") || key.equals("Team")) {
                if (val.contains(",")) val = val.split(",")[0].trim();
            }
            if (!val.isEmpty() && !val.equals("0")) {
                sb.append(key).append(":").append(val).append("|");
            }
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                String hex = Integer.toHexString(0xff & digest[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }
}
