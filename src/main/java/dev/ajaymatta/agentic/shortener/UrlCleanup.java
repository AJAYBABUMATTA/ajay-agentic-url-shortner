package dev.ajaymatta.agentic.shortener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UrlCleanup {
    private final UrlService service; private final boolean enabled;
    public UrlCleanup(UrlService service,@Value("${shortener.cleanup.enabled:true}") boolean enabled) { this.service=service; this.enabled=enabled; }
    @Scheduled(fixedDelayString="${shortener.cleanup.interval-ms:60000}") public void poll() { if(enabled) service.cleanup(); }
}
