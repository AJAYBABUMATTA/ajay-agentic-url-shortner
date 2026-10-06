package dev.ajaymatta.agentic.shortener;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UrlRateLimiter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc; private final Clock clock; private final ObjectMapper json;
    private final int limit,windowSeconds;
    public UrlRateLimiter(JdbcTemplate jdbc,@Qualifier("shortenerClock") Clock clock,ObjectMapper json,
            @Value("${shortener.rate-limit.limit:60}") int limit,@Value("${shortener.rate-limit.window-seconds:60}") int windowSeconds) {
        if(limit<1 || limit>100000 || windowSeconds<1 || windowSeconds>3600) throw new IllegalArgumentException("Rate policy must be bounded");
        this.jdbc=jdbc; this.clock=clock; this.json=json; this.limit=limit; this.windowSeconds=windowSeconds;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return !UrlCapabilities.enabled("rate-limit") || !(path.startsWith("/api/v1/urls") || path.matches("/[A-Za-z0-9_-]{3,64}") && !path.equals("/actuator"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        long now=clock.instant().getEpochSecond(), start=now-Math.floorMod(now,windowSeconds);
        // Trust the socket peer only: X-Forwarded-For is deliberately ignored.
        String identity=UrlService.digest(request.getRemoteAddr()); boolean allowed=false;
        for(int attempt=0;attempt<2;attempt++) {
            if(jdbc.update("UPDATE url_rate_buckets SET used=used+1 WHERE identity_hash=? AND window_start=? AND used<?",identity,start,limit)==1) { allowed=true; break; }
            if(jdbc.queryForObject("SELECT COUNT(*) FROM url_rate_buckets WHERE identity_hash=? AND window_start=?",Integer.class,identity,start)>0) break;
            try { jdbc.update("INSERT INTO url_rate_buckets VALUES (?,?,1)",identity,start); allowed=true; break; }
            catch(DuplicateKeyException race) { /* A concurrent first request won; recheck its remaining quota. */ }
        }
        if(allowed) { chain.doFilter(request,response); return; }
        response.setStatus(429); response.setContentType("application/problem+json"); response.setHeader("Retry-After",Long.toString(Math.max(1,start+windowSeconds-now)));
        json.writeValue(response.getOutputStream(),java.util.Map.of("type","urn:shortener:problem:rate-limit","title","Too Many Requests","status",429,"detail","Client request limit exceeded","instance",request.getRequestURI()));
    }
}
