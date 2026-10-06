package dev.ajaymatta.agentic.shortener;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class UrlRateLimiterTest {
    JdbcTemplate jdbc;
    @BeforeEach void database() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE url_rate_buckets(identity_hash VARCHAR(64),window_start BIGINT,used INTEGER,PRIMARY KEY(identity_hash,window_start))");
    }
    UrlRateLimiter limiter(long epoch,int limit) { return new UrlRateLimiter(jdbc,Clock.fixed(Instant.ofEpochSecond(epoch),ZoneOffset.UTC),new ObjectMapper(),limit,60); }
    MockHttpServletResponse request(UrlRateLimiter limiter,String path) throws Exception {
        var request=new MockHttpServletRequest("GET",path); request.setRemoteAddr("192.0.2.10"); request.addHeader("X-Forwarded-For",UUID.randomUUID().toString());
        var response=new MockHttpServletResponse(); limiter.doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(404)); return response;
    }
    @Test void quotaIsSharedAcrossInstancesAndSpoofedForwardingCannotBypassIt() throws Exception {
        assertThat(request(limiter(120,2),"/missing-one").getStatus()).isEqualTo(404);
        assertThat(request(limiter(120,2),"/missing-two").getStatus()).isEqualTo(404);
        var rejected=request(limiter(121,2),"/missing-three"); assertThat(rejected.getStatus()).isEqualTo(429); assertThat(rejected.getHeader("Retry-After")).isEqualTo("59");
        assertThat(rejected.getContentType()).isEqualTo("application/problem+json"); assertThat(rejected.getContentAsString()).contains("\"status\":429");
        assertThat(request(limiter(180,2),"/missing-four").getStatus()).isEqualTo(404);
    }
    @Test void concurrentQuotaCannotOvershootAndWorkflowEndpointsAreIndependent() throws Exception {
        try(var pool=Executors.newFixedThreadPool(12)) {
            var calls=new ArrayList<Callable<Integer>>(); for(int i=0;i<30;i++) calls.add(()->request(limiter(240,5),"/missing-code").getStatus());
            var statuses=new ArrayList<Integer>(); for(var future:pool.invokeAll(calls)) statuses.add(future.get());
            assertThat(statuses).filteredOn(s->s==404).hasSize(5); assertThat(statuses).filteredOn(s->s==429).hasSize(25);
        }
        assertThat(request(limiter(240,5),"/api/v1/workflows").getStatus()).isEqualTo(404);
        assertThat(request(limiter(240,5),"/actuator/health").getStatus()).isEqualTo(404);
    }
    @Test void invalidPoliciesFailAtStartup() {
        assertThatThrownBy(()->limiter(120,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new UrlRateLimiter(jdbc,Clock.systemUTC(),new ObjectMapper(),60,3601)).isInstanceOf(IllegalArgumentException.class);
    }
}
