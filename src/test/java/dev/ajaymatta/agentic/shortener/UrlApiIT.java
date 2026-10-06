package dev.ajaymatta.agentic.shortener;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"agentic.processing.enabled=false","shortener.cleanup.enabled=false","shortener.rate-limit.limit=1000"})
@ActiveProfiles("test") @Testcontainers @Import(UrlApiIT.DnsConfig.class)
class UrlApiIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",postgres::getJdbcUrl); p.add("spring.datasource.username",postgres::getUsername);
        p.add("spring.datasource.password",postgres::getPassword); p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @TestConfiguration static class DnsConfig { @Bean @Primary DnsResolver testDnsResolver() { return host->new InetAddress[]{InetAddress.getByAddress(host.equals("private.example") ? new byte[]{10,1,1,1} : new byte[]{8,8,8,8})}; } }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UrlService service;
    HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    HttpResponse<String> send(String path,String method,Object body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");
        if(token!=null) request.header("X-Link-Token",token);
        request.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode create(Map<String,?> body) throws Exception { var result=send("/api/v1/urls","POST",body,null); assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(201); return json.readTree(result.body()); }
    @Test void exactLocationInspectionOwnershipAndErrors() throws Exception {
        String target="https://example.com/path?query=1#fragment";
        var created=create(Map.of("target",target)); String code=created.path("code").asText(), token=created.path("managementToken").asText();
        assertThat(code).matches("[a-zA-Z0-9]{12}"); assertThat(token).hasSize(43);
        assertThat(jdbc.queryForObject("SELECT management_hash FROM short_urls WHERE code=?",String.class,code)).isEqualTo(UrlService.digest(token)).isNotEqualTo(token);
        var redirect=send("/"+code,"GET",null,null); assertThat(redirect.statusCode()).isEqualTo(302); assertThat(redirect.headers().firstValue("Location")).contains(target);
        var inspect=send("/api/v1/urls/"+code,"GET",null,null); assertThat(inspect.statusCode()).isEqualTo(200); assertThat(inspect.body()).doesNotContain(token,"management_hash","managementToken");
        assertThat(send("/api/v1/urls/"+code,"DELETE",null,"wrong").statusCode()).isEqualTo(403);
        assertThat(send("/api/v1/urls/"+code,"DELETE",null,token).statusCode()).isEqualTo(204);
        assertThat(send("/api/v1/urls/"+code,"DELETE",null,token).statusCode()).isEqualTo(204);
        assertThat(send("/"+code,"GET",null,null).statusCode()).isEqualTo(410);
        assertThat(service.analytics(code,null).total()).isEqualTo(1);
        var missing=send("/missing-code","GET",null,null); assertThat(missing.statusCode()).isEqualTo(404); assertThat(missing.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(send("/api/v1/urls","POST",Map.of("target","https://private.example"),null).statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/urls","POST",Map.of("target","https://example.com","unexpected",true),null).statusCode()).isEqualTo(400);
    }
    @Test void concurrentAliasClaimsHaveOneWinner() throws Exception {
        String alias="alias-"+UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var calls=new ArrayList<Callable<Integer>>(); for(int i=0;i<12;i++) calls.add(()->send("/api/v1/urls","POST",Map.of("target","https://example.com","alias",alias),null).statusCode());
            var statuses=new ArrayList<Integer>(); for(var future:pool.invokeAll(calls)) statuses.add(future.get());
            assertThat(statuses).filteredOn(s->s==201).hasSize(1); assertThat(statuses).filteredOn(s->s==409).hasSize(11);
        }
        assertThat(send("/api/v1/urls","POST",Map.of("target","https://example.com","alias","api"),null).statusCode()).isEqualTo(400);
    }
    @Test void concurrentRedirectsCountExactlyAndUtcDaysRemainIndependent() throws Exception {
        String code=create(Map.of("target","https://example.com")).path("code").asText();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var calls=new ArrayList<Callable<Integer>>(); for(int i=0;i<30;i++) calls.add(()->send("/"+code,"GET",null,null).statusCode());
            for(var future:pool.invokeAll(calls)) assertThat(future.get()).isEqualTo(302);
        }
        var analytics=service.analytics(code,null); assertThat(analytics.total()).isEqualTo(30);
        assertThat(analytics.daily()).containsEntry(LocalDate.now(ZoneOffset.UTC).toString(),30L);
        assertThat(service.analytics(code,LocalDate.now(ZoneOffset.UTC).minusDays(1)).daily().values()).containsExactly(0L);
        assertThat(service.inspect(code).totalClicks()).isEqualTo(30);
    }
    @Test void expiryAndCleanupPreserveGoneTombstonesWithoutCounts() throws Exception {
        String code=create(Map.of("target","https://example.com","expiresAt",Instant.now().plusSeconds(1).toString())).path("code").asText();
        Thread.sleep(1200); assertThat(send("/"+code,"GET",null,null).statusCode()).isEqualTo(410); assertThat(service.analytics(code,null).total()).isZero();
        jdbc.update("UPDATE short_urls SET expires_at=? WHERE code=?",OffsetDateTime.now(ZoneOffset.UTC).minusDays(31),code);
        assertThat(service.cleanup()).isGreaterThanOrEqualTo(1); assertThat(service.inspect(code).target()).isNull(); assertThat(service.inspect(code).active()).isFalse();
        assertThat(send("/"+code,"GET",null,null).statusCode()).isEqualTo(410);
        assertThat(send("/api/v1/urls","POST",Map.of("target","https://example.com","alias",code),null).statusCode()).isEqualTo(409);
    }
}
