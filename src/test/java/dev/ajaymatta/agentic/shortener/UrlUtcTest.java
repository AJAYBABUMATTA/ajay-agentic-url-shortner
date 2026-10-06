package dev.ajaymatta.agentic.shortener;

import java.net.InetAddress;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class UrlUtcTest {
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-10-06T23:59:59Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now,zone); }
        public Instant instant() { return now; }
    }
    @Test void midnightUsesTwoUtcBucketsAndExpiredDestinationNeverCounts() throws Exception {
        var datasource=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V5__persistent_shortener.sql")).execute(datasource);
        var clock=new MutableClock();
        try(var security=new UrlSecurity(host->new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})})) {
            var service=new UrlService(new JdbcTemplate(datasource),security,clock);
            var created=service.create("https://example.com","utc-example",clock.instant().plusSeconds(3));
            service.redirect(created.link().code()); clock.now=clock.now.plusSeconds(2); service.redirect(created.link().code());
            assertThat(service.analytics(created.link().code(),null).daily()).containsEntry("2026-10-06",1L).containsEntry("2026-10-07",1L);
            clock.now=clock.now.plusSeconds(2);
            assertThatThrownBy(()->service.redirect(created.link().code())).isInstanceOf(UrlFailure.class);
            assertThat(service.analytics(created.link().code(),null).total()).isEqualTo(2);
            assertThatThrownBy(()->service.create("https://example.com",null,clock.instant().plusSeconds(366L*86400))).isInstanceOf(UrlFailure.class);
            assertThatThrownBy(()->service.create("https://example.com","bad.alias",null)).isInstanceOf(UrlFailure.class);
            assertThatThrownBy(()->service.inspect("unknown-code")).isInstanceOf(UrlFailure.class);
        }
    }
    @Test void ipv6TransitionDestinationsAreRejected() throws Exception {
        try(var security=new UrlSecurity(host->new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})})) {
            assertThatThrownBy(()->security.validate("https://[2001:0:4136:e378:8000:63bf:3fff:fdd2]")).isInstanceOf(UrlFailure.class);
        }
    }
}
