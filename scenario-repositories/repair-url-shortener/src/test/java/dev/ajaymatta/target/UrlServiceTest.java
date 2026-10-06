package dev.ajaymatta.target;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrlServiceTest {
    @Test void createdTargetCanBeResolvedAndMissingCodeReturnsNull() {
        var service = new UrlService();
        String code = service.create("https://example.org/resource");
        assertEquals("https://example.org/resource", service.resolve(code));
        assertNull(service.resolve("missing"));
    }
}
