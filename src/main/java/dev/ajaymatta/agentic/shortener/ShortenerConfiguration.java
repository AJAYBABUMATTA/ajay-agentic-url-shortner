package dev.ajaymatta.agentic.shortener;

import java.net.InetAddress;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShortenerConfiguration {
    @Bean Clock shortenerClock() { return Clock.systemUTC(); }
    @Bean DnsResolver dnsResolver() { return InetAddress::getAllByName; }
}
