package dev.ajaymatta.agentic.engineering;

/** Original deterministic templates for the bounded create/redirect capability. */
final class GeneratedServiceSources {
    private GeneratedServiceSources() {}
    static final String ROOT = "src/main/java/dev/ajaymatta/generated/";
    static final String TEST = "src/test/java/dev/ajaymatta/generated/";
    static final String POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.0</version><relativePath/></parent>
              <groupId>dev.ajaymatta.generated</groupId><artifactId>generated-shortener</artifactId><version>1.0.0</version>
              <properties><java.version>21</java.version><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
              <dependencies>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
                <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
              </dependencies>
              <build><plugins>
                <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
                <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-enforcer-plugin</artifactId><version>3.5.0</version>
                  <executions><execution><goals><goal>enforce</goal></goals><configuration><rules><requireJavaVersion><version>[21,22)</version></requireJavaVersion></rules></configuration></execution></executions>
                </plugin>
                <plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><version>0.8.13</version>
                  <executions><execution><goals><goal>prepare-agent</goal></goals></execution><execution><id>coverage</id><phase>verify</phase><goals><goal>report</goal></goals></execution></executions>
                </plugin>
              </plugins></build>
            </project>
            """;
    static final String APPLICATION = """
            package dev.ajaymatta.generated;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            @SpringBootApplication
            public class ShortenerApplication {
                public static void main(String[] args) { SpringApplication.run(ShortenerApplication.class, args); }
            }
            """;
    static final String STORE = """
            package dev.ajaymatta.generated;
            import java.security.SecureRandom;
            import java.util.concurrent.ConcurrentHashMap;
            import org.springframework.stereotype.Service;
            @Service
            public class UrlStore {
                private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
                private final SecureRandom random = new SecureRandom();
                private final ConcurrentHashMap<String,String> targets = new ConcurrentHashMap<>();
                public String create(String target) {
                    for (int attempt=0; attempt<20; attempt++) {
                        StringBuilder code = new StringBuilder();
                        for (int i=0;i<10;i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
                        String value = code.toString();
                        if (targets.putIfAbsent(value,target)==null) return value;
                    }
                    throw new IllegalStateException("Code allocation exhausted");
                }
                public String resolve(String code) { return targets.get(code); }
            }
            """;
    static final String CREATE = """
            package dev.ajaymatta.generated;
            import java.net.URI;
            import org.springframework.http.*;
            import org.springframework.web.bind.annotation.*;
            @RestController
            public class UrlController {
                private final UrlStore store;
                public UrlController(UrlStore store) { this.store=store; }
                public record CreateRequest(String target) {}
                public record CreatedUrl(String code, String target) {}
                @PostMapping("/api/v1/urls")
                public ResponseEntity<?> create(@RequestBody CreateRequest request) {
                    try {
                        URI uri = URI.create(request.target());
                        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                                || uri.getHost()==null || uri.getUserInfo()!=null || request.target().length()>2048) throw new IllegalArgumentException();
                    } catch (RuntimeException invalid) {
                        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"A valid HTTP(S) target is required"));
                    }
                    String code=store.create(request.target());
                    return ResponseEntity.created(URI.create("/"+code)).body(new CreatedUrl(code,request.target()));
                }
            }
            """;
    static String redirect(int status) { return """
            package dev.ajaymatta.generated;
            import java.net.URI;
            import org.springframework.http.*;
            import org.springframework.web.bind.annotation.*;
            @RestController
            public class RedirectController {
                private final UrlStore store;
                public RedirectController(UrlStore store) { this.store=store; }
                @GetMapping("/{code}")
                public ResponseEntity<?> redirect(@PathVariable String code) {
                    String target=store.resolve(code);
                    if (target==null) return ResponseEntity.status(404).body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,"Unknown short code"));
                    return ResponseEntity.status(%d).location(URI.create(target)).build();
                }
            }
            """.formatted(status); }
    static final String TEST_IMPORTS = """
            package dev.ajaymatta.generated;
            import java.net.URI;
            import java.net.http.*;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.boot.test.web.server.LocalServerPort;
            import static org.assertj.core.api.Assertions.*;
            @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
            """;
    static final String CREATE_TEST = TEST_IMPORTS + """
            class CreateUrlTest {
                @LocalServerPort int port;
                final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
                HttpResponse<String> create(String body) throws Exception {
                    return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/urls"))
                        .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
                }
                @Test void validTargetCreatesUniqueUsableCodes() throws Exception {
                    String body=new ObjectMapper().writeValueAsString(java.util.Map.of("target","https://example.com/path?q=1"));
                    var first=create(body);
                    var second=create(body);
                    assertThat(first.statusCode()).isEqualTo(201); assertThat(second.statusCode()).isEqualTo(201);
                    var json=new ObjectMapper();
                    String code=json.readTree(first.body()).get("code").asText();
                    assertThat(code).matches("[a-zA-Z0-9]{10}");
                    assertThat(json.readTree(first.body()).get("target").asText()).isEqualTo("https://example.com/path?q=1");
                    assertThat(first.headers().firstValue("Location")).contains("/"+code);
                    assertThat(json.readTree(second.body()).get("code").asText()).isNotEqualTo(code);
                }
                @Test void rejectsUnsupportedSchemeMissingHostAndCredentials() throws Exception {
                    for(String target : new String[]{"file:///etc/passwd","https:///missing","https://user:password@example.com"}) {
                        var response=create(new ObjectMapper().writeValueAsString(java.util.Map.of("target",target)));
                        assertThat(response.statusCode()).isEqualTo(400);
                        assertThat(new ObjectMapper().readTree(response.body()).get("status").asInt()).isEqualTo(400);
                    }
                }
                @Test void rejectsMissingTarget() throws Exception { assertThat(create("{}").statusCode()).isEqualTo(400); }
            }
            """;
    static String redirectTest(int status) { return TEST_IMPORTS + """
            class RedirectUrlTest {
                @LocalServerPort int port;
                final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
                @Test void createdCodeRedirectsToExactStoredTargetThroughHttp() throws Exception {
                    String target="https://example.com/path?q=two#part";
                    var created=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/urls"))
                        .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(java.util.Map.of("target",target)))).build(),HttpResponse.BodyHandlers.ofString());
                    assertThat(created.statusCode()).isEqualTo(201);
                    String code=new ObjectMapper().readTree(created.body()).get("code").asText();
                    var redirect=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/"+code)).GET().build(),HttpResponse.BodyHandlers.ofString());
                    assertThat(redirect.statusCode()).isEqualTo(%d);
                    assertThat(redirect.headers().firstValue("Location")).contains(target);
                }
                @Test void unknownCodeReturns404WithoutLocation() throws Exception {
                    var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/unknown-code")).GET().build(),HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(404);
                    assertThat(response.headers().firstValue("Location")).isEmpty();
                }
            }
            """.formatted(status); }
}
