package dev.ajaymatta.agentic.shortener;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlService {
    public record Link(String code,String target,boolean active,Instant expiresAt,Instant createdAt,long totalClicks) {}
    public record Created(Link link,String managementToken) {}
    public record Analytics(String code,long total,Map<String,Long> daily) {}
    private static final String ALPHABET="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final SecureRandom random=new SecureRandom();
    private final JdbcTemplate jdbc;
    private final UrlSecurity security;
    private final Clock clock;
    public UrlService(JdbcTemplate jdbc,UrlSecurity security,@Qualifier("shortenerClock") Clock clock) { this.jdbc=jdbc; this.security=security; this.clock=clock; }
    public Created create(String target,String alias,Instant expiry) {
        String safe=security.validate(target); Instant now=now();
        if(expiry!=null && (!expiry.isAfter(now) || expiry.isAfter(now.plus(365,ChronoUnit.DAYS)))) throw new UrlFailure(400,"Expiry must be in the future and within 365 days");
        if(alias!=null && (!alias.matches("[A-Za-z0-9_-]{3,64}") || Set.of("api","actuator","error","health","v3","swagger-ui","mvnw").contains(alias.toLowerCase(Locale.ROOT)))) throw new UrlFailure(400,"Alias is invalid or reserved");
        byte[] secret=new byte[32]; random.nextBytes(secret); String token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        for(int attempt=0;attempt<20;attempt++) {
            String code=alias==null ? randomCode() : alias;
            try {
                jdbc.update("INSERT INTO short_urls(code,target,management_hash,active,expires_at,created_at,total_clicks) VALUES (?,?,?,?,?,?,0)",code,safe,digest(token),true,time(expiry),time(now));
                return new Created(new Link(code,safe,true,expiry,now,0),token);
            } catch(DuplicateKeyException collision) { if(alias!=null) throw new UrlFailure(409,"Alias already exists"); }
        }
        throw new UrlFailure(503,"Code allocation exhausted; retry later");
    }
    @Transactional public String redirect(String code) {
        Link link=find(code,true);
        if(!link.active() || link.expiresAt()!=null && !link.expiresAt().isAfter(now())) throw new UrlFailure(410,"Short URL is inactive or expired");
        // Revalidate destination before counting or returning Location; the server never fetches it.
        String target=security.validate(link.target());
        jdbc.update("UPDATE short_urls SET total_clicks=total_clicks+1 WHERE code=?",code);
        java.sql.Date day=java.sql.Date.valueOf(LocalDate.now(clock));
        if(jdbc.update("UPDATE url_daily_clicks SET clicks=clicks+1 WHERE code=? AND utc_day=?",code,day)==0)
            jdbc.update("INSERT INTO url_daily_clicks VALUES (?,?,1)",code,day);
        return target;
    }
    public Link inspect(String code) { return find(code,false); }
    @Transactional public void deactivate(String code,String token) {
        find(code,true);
        String expected=jdbc.queryForObject("SELECT management_hash FROM short_urls WHERE code=?",String.class,code);
        if(token==null || token.length()>256 || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),digest(token).getBytes(StandardCharsets.US_ASCII))) throw new UrlFailure(403,"A valid link management token is required");
        jdbc.update("UPDATE short_urls SET active=FALSE WHERE code=?",code);
    }
    @Transactional public Analytics analytics(String code,LocalDate day) {
        Link link=find(code,true); Map<String,Long> daily=new TreeMap<>();
        if(day==null) jdbc.query("SELECT utc_day,clicks FROM url_daily_clicks WHERE code=? ORDER BY utc_day",(org.springframework.jdbc.core.RowCallbackHandler) r->daily.put(r.getDate(1).toLocalDate().toString(),r.getLong(2)),code);
        else daily.put(day.toString(),Optional.ofNullable(jdbc.query("SELECT clicks FROM url_daily_clicks WHERE code=? AND utc_day=?",(r,n)->r.getLong(1),code,java.sql.Date.valueOf(day)).stream().findFirst().orElse(null)).orElse(0L));
        return new Analytics(code,link.totalClicks(),Map.copyOf(daily));
    }
    public int cleanup() {
        Instant now=now();
        jdbc.update("UPDATE short_urls SET active=FALSE WHERE expires_at<=? AND active=TRUE",time(now));
        // Preserve code tombstones (410 and no alias reuse), remove destination after retention.
        int cleared=jdbc.update("UPDATE short_urls SET target=NULL WHERE expires_at<? AND target IS NOT NULL",time(now.minus(30,ChronoUnit.DAYS)));
        jdbc.update("DELETE FROM url_daily_clicks WHERE utc_day<?",java.sql.Date.valueOf(LocalDate.now(clock).minusDays(365)));
        jdbc.update("DELETE FROM url_rate_buckets WHERE window_start<?",now.minus(1,ChronoUnit.DAYS).getEpochSecond());
        return cleared;
    }
    private Link find(String code,boolean lock) {
        var links=jdbc.query("SELECT * FROM short_urls WHERE code=?"+(lock ? " FOR UPDATE" : ""),(r,n)->new Link(r.getString("code"),r.getString("target"),r.getBoolean("active"),instant(r.getObject("expires_at",OffsetDateTime.class)),r.getObject("created_at",OffsetDateTime.class).toInstant(),r.getLong("total_clicks")),code);
        if(links.isEmpty()) throw new UrlFailure(404,"Unknown short code"); return links.getFirst();
    }
    private String randomCode() { StringBuilder value=new StringBuilder(); for(int i=0;i<12;i++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length()))); return value.toString(); }
    static String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static Instant instant(OffsetDateTime value) { return value==null ? null : value.toInstant(); }
    private static OffsetDateTime time(Instant value) { return value==null ? null : value.atOffset(ZoneOffset.UTC); }
}
