package de.maulmann;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TriviaManagerTest {

    @Test
    void testFaqItemRecord() {
        TriviaManager.FaqItem item = new TriviaManager.FaqItem("Is this card graded?", "Yes, PSA 10.");
        assertEquals("Is this card graded?", item.question());
        assertEquals("Yes, PSA 10.", item.answer());
    }

    @Test
    void testTriviaManagerLoadAndMatch() {
        TriviaManager manager = new TriviaManager();
        Map<String, String> cardData = new HashMap<>();
        cardData.put("Season", "1994-95");
        cardData.put("Player", "Juwan Howard");
        cardData.put("Brand", "Collector's Choice");
        cardData.put("Rookie", "Yes");

        List<TriviaManager.FaqItem> faqs = manager.getFaqs("rookieFaq", cardData);
        assertNotNull(faqs, "getFaqs should never return null.");
        assertFalse(faqs.isEmpty(), "rookieFaq should match 1994-95 season.");
    }

    @Test
    void testCaseInsensitiveKeyMatching() {
        TriviaManager manager = TriviaManager.getInstance();
        Map<String, String> cardData = new HashMap<>();
        // Lowercase keys
        cardData.put("theme", "Triumvirate");
        cardData.put("player", "Juwan Howard");

        String trivia = manager.getTrivia("hobbyTrivia", cardData);
        assertTrue(trivia.contains("Stadium Club Triumvirate"), "Should match Triumvirate even with lowercase 'theme' key.");
    }

    @Test
    void testHobbyTriviaInsertsAndSubsets() {
        TriviaManager manager = TriviaManager.getInstance();

        // Essential Credentials
        String credentialsTrivia = manager.getTrivia("hobbyTrivia", Map.of("Theme", "Essential Credentials"));
        assertTrue(credentialsTrivia.contains("Essential Credentials"), "Should match Essential Credentials");

        // Autographics
        String autographicsTrivia = manager.getTrivia("hobbyTrivia", Map.of("Theme", "Autographics"));
        assertTrue(autographicsTrivia.contains("Autographics"), "Should match Autographics");

        // StarQuest
        String starQuestTrivia = manager.getTrivia("hobbyTrivia", Map.of("Theme", "StarQuest"));
        assertTrue(starQuestTrivia.contains("StarQuest"), "Should match StarQuest");

        // Crash The Game
        String crashTrivia = manager.getTrivia("hobbyTrivia", Map.of("Theme", "Crash The Game"));
        assertTrue(crashTrivia.contains("Crash The Game"), "Should match Crash The Game");

        // Members Only
        String membersTrivia = manager.getTrivia("hobbyTrivia", Map.of("Variant", "Members Only"));
        assertTrue(membersTrivia.contains("Members Only"), "Should match Members Only");

        // SPx
        String spxTrivia = manager.getTrivia("hobbyTrivia", Map.of("Brand", "SPx"));
        assertTrue(spxTrivia.contains("SPx redefined the hobby"), "Should match SPx");

        // Gold Label
        String goldLabelTrivia = manager.getTrivia("hobbyTrivia", Map.of("Brand", "Gold Label"));
        assertTrue(goldLabelTrivia.contains("Gold Label"), "Should match Gold Label");

        // Platinum Medallion
        String platTrivia = manager.getTrivia("hobbyTrivia", Map.of("Variant", "Platinum Medallion"));
        assertTrue(platTrivia.contains("Platinum Medallions"), "Should match Platinum Medallion");
    }

    @Test
    void testTechTriviaPrintingAndFinishing() {
        TriviaManager manager = TriviaManager.getInstance();

        // Super Short Print /5
        String ssp5Trivia = manager.getTrivia("techTrivia", Map.of("Print Run", "5"));
        assertTrue(ssp5Trivia.contains("Super Short Print (/5)"), "Should match /5 print run");

        // Short Print /25
        String sp25Trivia = manager.getTrivia("techTrivia", Map.of("Print Run", "25"));
        assertTrue(sp25Trivia.contains("Short Print Parallel (/25)"), "Should match /25 print run");

        // Gold Refractor
        String goldRefTrivia = manager.getTrivia("techTrivia", Map.of("Variant", "Gold Refractor"));
        assertTrue(goldRefTrivia.contains("Gold Refractor"), "Should match Gold Refractor");

        // Blue Refractor
        String blueRefTrivia = manager.getTrivia("techTrivia", Map.of("Variant", "Blue Refractor"));
        assertTrue(blueRefTrivia.contains("Blue Refractor"), "Should match Blue Refractor");

        // 1/1 Masterpiece
        String oneOfOneTrivia = manager.getTrivia("techTrivia", Map.of("Variant", "1 of 1"));
        assertTrue(oneOfOneTrivia.contains("One-of-One"), "Should match 1 of 1");
    }

    @Test
    void testPlayerHighlightsTeamAndTeammateMatching() {
        TriviaManager manager = TriviaManager.getInstance();

        // Washington Bullets
        String bullets = manager.getTrivia("playerHighlights", Map.of("Team", "Washington Bullets"));
        assertTrue(bullets.contains("Capital Centre Bullets Era"), "Should match Bullets highlights");

        // Washington Wizards
        String wizards = manager.getTrivia("playerHighlights", Map.of("Team", "Washington Wizards"));
        assertTrue(wizards.contains("Inaugural Wizards MCI Center Era"), "Should match Wizards highlights");

        // Houston Rockets
        String rockets = manager.getTrivia("playerHighlights", Map.of("Team", "Houston Rockets"));
        assertTrue(rockets.contains("Rockets Frontcourt Anchor"), "Should match Rockets highlights");

        // Miami Heat
        String heat = manager.getTrivia("playerHighlights", Map.of("Team", "Miami Heat"));
        assertTrue(heat.contains("Heatles Championship Glory"), "Should match Heat highlights");

        // Michigan Wolverines
        String michigan = manager.getTrivia("playerHighlights", Map.of("Team", "Michigan Wolverines"));
        assertTrue(michigan.contains("Fab Five Cultural Revolution"), "Should match Wolverines highlights");

        // Chris Webber teammate card
        String webber = manager.getTrivia("playerHighlights", Map.of("Player", "Chris Webber"));
        assertTrue(webber.contains("Fab Five & Bullets Brotherhood"), "Should match Webber teammate highlight");

        // Jett Howard
        String jett = manager.getTrivia("playerHighlights", Map.of("Player", "Jett Howard"));
        assertTrue(jett.contains("Generational Legacy"), "Should match Jett Howard highlight");
    }

    @Test
    void testEraContextTeamMatching() {
        TriviaManager manager = TriviaManager.getInstance();

        String bulletsEra = manager.getTrivia("eraContext", Map.of("Team", "Washington Bullets"));
        assertTrue(bulletsEra.contains("1990s Washington Bullets Era"), "Should match Bullets era context");

        String heatEra = manager.getTrivia("eraContext", Map.of("Team", "Miami Heat"));
        assertTrue(heatEra.contains("Heatles"), "Should match Heatles era context");
    }
}
