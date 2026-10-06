package dev.ajaymatta.agentic.engineering;

import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Generates original platform-owned URL service source into an isolated standalone target. */
@Component
public class FullShortenerSources {
    public static final String ROOT="src/main/java/dev/ajaymatta/generated/shortener/";
    public static final String TEST="src/test/java/dev/ajaymatta/generated/shortener/";
    public static final List<String> TYPES=List.of("DnsResolver","ShortenerConfiguration","UrlCapabilities","UrlCleanup","UrlController","UrlFailure","UrlProblemHandler","UrlRateLimiter","UrlSecurity","UrlService");
    public static final Set<String> CAPABILITIES=Set.of("create","redirect","alias","expiry","inspect","deactivate","analytics-total","analytics-daily","rate-limit");
    public static final String POM=GeneratedServiceSources.POM.replace("<build><plugins>","""
            <build><plugins>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><configuration><parameters>true</parameters></configuration></plugin>
            """).replace("<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>","""
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
            <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
            <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.8.9</version></dependency>
            <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId></dependency>
            """);
    private final Map<String,String> files;
    public FullShortenerSources(@Value("${agentic.build.assets-root:.}") String assetsRoot) {
        try {
            Path root=Path.of(assetsRoot).toAbsolutePath().normalize(); Map<String,String> content=new TreeMap<>();
            for(String type:TYPES) content.put(ROOT+type+".java",Files.readString(root.resolve("src/main/java/dev/ajaymatta/agentic/shortener/"+type+".java")).replace("dev.ajaymatta.agentic.shortener","dev.ajaymatta.generated.shortener"));
            content.put("src/main/resources/db/migration/V1__shortener.sql",Files.readString(root.resolve("src/main/resources/db/migration/V5__persistent_shortener.sql")));
            content.put("src/main/resources/application.yaml","""
                    spring:
                      datasource:
                        url: ${DB_URL:jdbc:postgresql://localhost:5432/shortener}
                        username: ${DB_USERNAME:agentic}
                        password: ${DB_PASSWORD}
                      mvc:
                        problemdetails:
                          enabled: true
                      jackson:
                        deserialization:
                          fail-on-unknown-properties: true
                    management:
                      endpoints:
                        web:
                          exposure:
                            include: health,prometheus
                    """);
            content.put(GeneratedServiceSources.ROOT+"ShortenerApplication.java",GeneratedServiceSources.APPLICATION.replace("@SpringBootApplication","@org.springframework.scheduling.annotation.EnableScheduling\n@SpringBootApplication"));
            files=Map.copyOf(content);
        } catch(java.io.IOException failure) { throw new IllegalStateException("Original complete target source unavailable",failure); }
    }
    public Map<String,String> production() {
        var result=new TreeMap<>(files); result.put("pom.xml",POM);
        result.put(ROOT+"UrlCapabilities.java",enable(result.get(ROOT+"UrlCapabilities.java"),"create",302)); return result;
    }
    public static List<String> productionPaths() {
        var paths=new ArrayList<>(TYPES.stream().map(type->ROOT+type+".java").toList());
        paths.addAll(List.of("pom.xml","mvnw","mvnw.cmd",".mvn/wrapper/maven-wrapper.properties",GeneratedServiceSources.ROOT+"ShortenerApplication.java","src/main/resources/application.yaml","src/main/resources/db/migration/V1__shortener.sql")); return List.copyOf(paths);
    }
    public static String enable(String content,String capability,int redirectStatus) {
        String marker="private static final Set<String> ENABLED = Set.of(";
        int start=content.indexOf(marker), end=content.indexOf(");",start);
        if(start<0 || end<0 || !CAPABILITIES.contains(capability)) throw new IllegalArgumentException("Unsupported capability source");
        Set<String> enabled=new TreeSet<>();
        if(!capability.equals("create")) {
            var matcher=java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(content.substring(start+marker.length(),end));
            while(matcher.find()) enabled.add(matcher.group(1));
        }
        enabled.add(capability);
        String declaration=enabled.stream().map(value->"\""+value+"\"").collect(java.util.stream.Collectors.joining(", "));
        return content.substring(0,start+marker.length())+declaration+content.substring(end).replaceAll("REDIRECT_STATUS = [0-9]+","REDIRECT_STATUS = "+redirectStatus);
    }
    public static String testPath(String capability) { return TEST+className(capability)+".java"; }
    public static String className(String capability) { return "Feature"+Arrays.stream(capability.split("-")).map(p->Character.toUpperCase(p.charAt(0))+p.substring(1)).collect(java.util.stream.Collectors.joining())+"Test"; }
    public static String caseName(String capability) { return switch(capability) {
        case "create" -> "createsUniquePersistentCodesAndRejectsUnsafeDestinations";
        case "redirect" -> "redirectsToExactLocationAndUnknownReturns404";
        case "alias" -> "aliasDuplicateAndReservedNamesAreRejected";
        case "expiry" -> "expiredLinksReturn410WithoutCounting";
        case "inspect" -> "inspectionDoesNotIncrementClicksOrExposeSecret";
        case "deactivate" -> "onlyManagementTokenCanDeactivateAndGoneDoesNotCount";
        case "analytics-total" -> "onlySuccessfulRedirectsIncrementTotal";
        case "analytics-daily" -> "utcDayCountsAndAbsentDayReturnsZero";
        case "rate-limit" -> "excessRequestsReturn429AndRetryAfter";
        default -> throw new IllegalArgumentException("Unknown capability");
    }; }
    public static String test(String capability,int status) {
        String body=switch(capability) {
            case "create" -> """
                var first=create(Map.of("target","https://example.com/path?q=1")); var second=create(Map.of("target","https://example.com/path?q=1"));
                assertThat(first.get("code").asText()).matches("[a-zA-Z0-9]{12}").isNotEqualTo(second.get("code").asText());
                assertThat(first.get("managementToken").asText()).hasSize(43);
                for(String target:List.of("file:///etc/passwd","http://127.0.0.1","http://[::1]","https://user:password@example.com","https://private.example","http://2130706433","http://0177.0.0.1"))
                    assertThat(send("/api/v1/urls","POST",json.writeValueAsString(Map.of("target",target)),null).statusCode()).isEqualTo(400);
                """;
            case "redirect" -> """
                String target="https://example.com/path?q=two#part"; String code=create(Map.of("target",target)).get("code").asText();
                var redirect=get("/"+code); assertThat(redirect.statusCode()).isEqualTo(STATUS);
                assertThat(redirect.headers().firstValue("Location")).contains(target);
                assertThat(get("/unknown-code").statusCode()).isEqualTo(404);
                """.replace("STATUS",Integer.toString(status));
            case "alias" -> """
                String alias="alias"+UUID.randomUUID().toString().replace("-","");
                assertThat(create(Map.of("target","https://example.com","alias",alias)).get("code").asText()).isEqualTo(alias);
                assertThat(send("/api/v1/urls","POST",json.writeValueAsString(Map.of("target","https://example.com","alias",alias)),null).statusCode()).isEqualTo(409);
                assertThat(send("/api/v1/urls","POST",json.writeValueAsString(Map.of("target","https://example.com","alias","api")),null).statusCode()).isEqualTo(400);
                """;
            case "expiry" -> """
                String code=create(Map.of("target","https://example.com","expiresAt",Instant.now().plusMillis(1200).toString())).get("code").asText();
                Thread.sleep(1500); assertThat(get("/"+code).statusCode()).isEqualTo(410);
                assertThat(send("/api/v1/urls","POST",json.writeValueAsString(Map.of("target","https://example.com","expiresAt",Instant.now().minusSeconds(1).toString())),null).statusCode()).isEqualTo(400);
                """;
            case "inspect" -> """
                var created=create(Map.of("target","https://example.com")); var response=get("/api/v1/urls/"+created.get("code").asText());
                assertThat(response.statusCode()).isEqualTo(200); var inspected=json.readTree(response.body());
                assertThat(inspected.get("totalClicks").asLong()).isZero(); assertThat(inspected.has("managementToken")).isFalse(); assertThat(inspected.has("management_hash")).isFalse();
                """;
            case "deactivate" -> """
                var created=create(Map.of("target","https://example.com")); String code=created.get("code").asText();
                assertThat(send("/api/v1/urls/"+code,"DELETE",null,"wrong-token").statusCode()).isEqualTo(403);
                assertThat(send("/api/v1/urls/"+code,"DELETE",null,created.get("managementToken").asText()).statusCode()).isEqualTo(204);
                assertThat(get("/"+code).statusCode()).isEqualTo(410);
                """;
            case "analytics-total" -> """
                String code=create(Map.of("target","https://example.com")).get("code").asText(); get("/"+code); get("/"+code);
                var response=get("/api/v1/urls/"+code+"/analytics"); assertThat(response.statusCode()).isEqualTo(200);
                assertThat(json.readTree(response.body()).get("total").asLong()).isEqualTo(2);
                get("/api/v1/urls/"+code+"/analytics"); assertThat(json.readTree(get("/api/v1/urls/"+code+"/analytics").body()).get("total").asLong()).isEqualTo(2);
                """;
            case "analytics-daily" -> """
                String code=create(Map.of("target","https://example.com")).get("code").asText(); get("/"+code);
                String today=LocalDate.now(Clock.systemUTC()).toString(), yesterday=LocalDate.now(Clock.systemUTC()).minusDays(1).toString();
                assertThat(json.readTree(get("/api/v1/urls/"+code+"/analytics?day="+today).body()).get("daily").get(today).asLong()).isEqualTo(1);
                assertThat(json.readTree(get("/api/v1/urls/"+code+"/analytics?day="+yesterday).body()).get("daily").get(yesterday).asLong()).isZero();
                """;
            case "rate-limit" -> """
                assertThat(get("/missing-one").statusCode()).isEqualTo(404); assertThat(get("/missing-two").statusCode()).isEqualTo(404);
                var rejected=get("/missing-three"); assertThat(rejected.statusCode()).isEqualTo(429);
                assertThat(Long.parseLong(rejected.headers().firstValue("Retry-After").orElseThrow())).isPositive();
                assertThat(json.readTree(rejected.body()).get("status").asInt()).isEqualTo(429);
                """;
            default -> throw new IllegalArgumentException("Unknown capability");
        };
        return """
            package dev.ajaymatta.generated.shortener;
            import java.time.*;
            import java.util.*;
            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.context.annotation.Import;
            import static org.assertj.core.api.Assertions.*;
            @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"shortener.cleanup.enabled=false","shortener.rate-limit.window-seconds=3600","shortener.rate-limit.limit=%d"})
            @Import(HttpSupport.DnsConfig.class)
            class %s extends HttpSupport {
                @Test void %s() throws Exception {
                    %s
                }
            }
            """.formatted(capability.equals("rate-limit") ? 2 : 1000,className(capability),caseName(capability),body);
    }
    public static final String TEST_CONFIG="""
            spring:
              datasource:
                url: jdbc:h2:mem:${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1
                username: sa
                password: test-only
                driver-class-name: org.h2.Driver
            """;
    public static final String HTTP_SUPPORT="""
            package dev.ajaymatta.generated.shortener;
            import java.net.*;
            import java.net.http.*;
            import java.util.*;
            import com.fasterxml.jackson.databind.*;
            import org.springframework.boot.test.web.server.LocalServerPort;
            import org.springframework.boot.test.context.TestConfiguration;
            import org.springframework.context.annotation.*;
            import org.springframework.test.context.ActiveProfiles;
            import static org.assertj.core.api.Assertions.*;
            @ActiveProfiles("test")
            abstract class HttpSupport {
                @LocalServerPort int port;
                final ObjectMapper json=new ObjectMapper();
                final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
                HttpResponse<String> send(String path,String method,String body,String token) throws Exception {
                    var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
                    if(token!=null) request.header("X-Link-Token",token);
                    request.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
                    return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
                }
                HttpResponse<String> get(String path) throws Exception { return send(path,"GET",null,null); }
                JsonNode create(Map<String,?> body) throws Exception {
                    var response=send("/api/v1/urls","POST",json.writeValueAsString(body),null);
                    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(201); return json.readTree(response.body());
                }
                @TestConfiguration static class DnsConfig {
                    @Bean @Primary DnsResolver testDnsResolver() { return host->new InetAddress[]{InetAddress.getByAddress(host.equals("private.example") ? new byte[]{10,1,1,1} : new byte[]{8,8,8,8})}; }
                }
            }
            """;
}