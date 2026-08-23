package de.maulmann;

import freemarker.template.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CardUtils Utility Tests")
class CardUtilsTest {

    @Test
    @DisplayName("escapeHtml should safely encode special characters and prevent double-encoding")
    void testEscapeHtml() {
        assertEquals("", CardUtils.escapeHtml(null));
        assertEquals("", CardUtils.escapeHtml(""));
        assertEquals("Plain text", CardUtils.escapeHtml("Plain text"));

        // Special characters escaping
        assertEquals("&lt;div class=&quot;test&quot;&gt;Juwan &amp; Team &#39;98&lt;/div&gt;",
                CardUtils.escapeHtml("<div class=\"test\">Juwan & Team '98</div>"));

        // Already escaped input should not be double-encoded
        assertEquals("&lt;b&gt;Bold &amp; Strong&lt;/b&gt;",
                CardUtils.escapeHtml("&lt;b&gt;Bold &amp; Strong&lt;/b&gt;"));
    }

    @Test
    @DisplayName("escapeJson should escape backslashes, quotes, and whitespace control chars")
    void testEscapeJson() {
        assertEquals("", CardUtils.escapeJson(null));
        assertEquals("", CardUtils.escapeJson(""));
        assertEquals("Simple string", CardUtils.escapeJson("Simple string"));

        String input = "Line 1\nLine 2\r\nTab:\tQuote: \"Slash: \\";
        String expected = "Line 1\\nLine 2\\r\\nTab:\\tQuote: \\\"Slash: \\\\";
        assertEquals(expected, CardUtils.escapeJson(input));
    }

    @Test
    @DisplayName("formatMulti should normalize comma delimiters to slash format")
    void testFormatMulti() {
        assertEquals("", CardUtils.formatMulti(null));
        assertEquals("", CardUtils.formatMulti(""));
        assertEquals("Single Value", CardUtils.formatMulti("Single Value"));
        assertEquals("Red / Blue / Gold", CardUtils.formatMulti("Red, Blue, Gold"));
        assertEquals("Fleer / SkyBox / Topps", CardUtils.formatMulti("Fleer ,  SkyBox,Topps"));
    }

    @Test
    @DisplayName("isValidForDisplay should reject invalid or placeholder strings")
    void testIsValidForDisplay() {
        assertFalse(CardUtils.isValidForDisplay(null));
        assertFalse(CardUtils.isValidForDisplay(""));
        assertFalse(CardUtils.isValidForDisplay("   "));
        assertFalse(CardUtils.isValidForDisplay("0"));
        assertFalse(CardUtils.isValidForDisplay("-"));
        assertFalse(CardUtils.isValidForDisplay("—"));

        assertTrue(CardUtils.isValidForDisplay("Juwan Howard"));
        assertTrue(CardUtils.isValidForDisplay("1997-98"));
        assertTrue(CardUtils.isValidForDisplay("1/1"));
    }

    @Test
    @DisplayName("isValidForSchema should reject placeholder and null-like strings")
    void testIsValidForSchema() {
        assertFalse(CardUtils.isValidForSchema(null));
        assertFalse(CardUtils.isValidForSchema(""));
        assertFalse(CardUtils.isValidForSchema("   "));
        assertFalse(CardUtils.isValidForSchema("null"));
        assertFalse(CardUtils.isValidForSchema("NULL"));
        assertFalse(CardUtils.isValidForSchema("Null"));
        assertFalse(CardUtils.isValidForSchema("-"));

        assertTrue(CardUtils.isValidForSchema("0"));
        assertTrue(CardUtils.isValidForSchema("Mint 10"));
        assertTrue(CardUtils.isValidForSchema("SportsCard"));
    }

    @Test
    @DisplayName("getFreeMarkerConfig should return configured singleton instance")
    void testGetFreeMarkerConfig() {
        Configuration cfg = CardUtils.getFreeMarkerConfig();
        assertNotNull(cfg);
        assertEquals("UTF-8", cfg.getDefaultEncoding());
        assertSame(cfg, CardUtils.getFreeMarkerConfig(), "Must return identical singleton instance");
    }
}
