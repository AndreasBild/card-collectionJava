package de.maulmann;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalDevPipeline Tests")
class LocalDevPipelineTest {

    @Test
    @DisplayName("getMimeType should return correct Content-Type for all web and image assets")
    void testGetMimeType() {
        assertEquals("image/avif", LocalDevPipeline.getMimeType("card.avif"));
        assertEquals("image/webp", LocalDevPipeline.getMimeType("card.webp"));
        assertEquals("image/jpeg", LocalDevPipeline.getMimeType("photo.jpg"));
        assertEquals("image/jpeg", LocalDevPipeline.getMimeType("photo.jpeg"));
        assertEquals("image/png", LocalDevPipeline.getMimeType("icon.png"));
        assertEquals("image/gif", LocalDevPipeline.getMimeType("anim.gif"));
        assertEquals("image/svg+xml", LocalDevPipeline.getMimeType("vector.svg"));
        assertEquals("image/x-icon", LocalDevPipeline.getMimeType("favicon.ico"));
        assertEquals("text/html; charset=utf-8", LocalDevPipeline.getMimeType("index.html"));
        assertEquals("text/css; charset=utf-8", LocalDevPipeline.getMimeType("main.css"));
        assertEquals("application/javascript; charset=utf-8", LocalDevPipeline.getMimeType("app.js"));
        assertEquals("application/json; charset=utf-8", LocalDevPipeline.getMimeType("cards.json"));
        assertEquals("application/xml; charset=utf-8", LocalDevPipeline.getMimeType("sitemap.xml"));
        assertEquals("text/plain; charset=utf-8", LocalDevPipeline.getMimeType("llms.txt"));
        assertEquals("application/octet-stream", LocalDevPipeline.getMimeType("binary.bin"));
    }
}
