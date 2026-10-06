package dev.ajaymatta.agentic.engineering;

import java.util.*;

/** Conservative edits to the original supported fixture's existing runtime. */
public final class BrownfieldSources {
    private BrownfieldSources() {}
    public static final String ROOT="src/main/java/dev/ajaymatta/target/";
    public static final String TEST="src/test/java/dev/ajaymatta/target/";
    public static Map<String,String> total(Map<String,String> baseline) {
        String service=required(baseline,ROOT+"UrlService.java"), controller=required(baseline,ROOT+"UrlController.java");
        String marker="    public String resolve(String code) { return targets.get(code); }";
        if(!service.contains(marker) || !controller.contains("String target = service.resolve(code);")) throw new IllegalArgumentException("Unsupported brownfield runtime structure");
        service=service.replace(marker,"""
                    private final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.LongAdder> clicks = new java.util.concurrent.ConcurrentHashMap<>();
                    public void recordRedirect(String code) {
                        if (targets.containsKey(code)) clicks.computeIfAbsent(code,key -> new java.util.concurrent.atomic.LongAdder()).increment();
                    }
                    public long totalClicks(String code) {
                        if (!targets.containsKey(code)) return -1;
                        var counter=clicks.get(code); return counter==null ? 0 : counter.sum();
                    }
                """+marker);
        controller=controller.replace("String target = service.resolve(code);","String target = service.resolve(code);\n        if (target != null) service.recordRedirect(code);");
        int end=controller.lastIndexOf('}');
        controller=controller.substring(0,end)+"""
                    @GetMapping("/api/v1/urls/{code}/analytics")
                    public ResponseEntity<?> analytics(@PathVariable String code) {
                        long total=service.totalClicks(code);
                        return total<0 ? ResponseEntity.notFound().build() : ResponseEntity.ok(Map.of("total",total));
                    }
                }
                """;
        return Map.of(ROOT+"UrlService.java",service,ROOT+"UrlController.java",controller);
    }
    public static Map<String,String> daily(Map<String,String> baseline) {
        String service=required(baseline,ROOT+"UrlService.java"),controller=required(baseline,ROOT+"UrlController.java");
        String increment="if (targets.containsKey(code)) clicks.computeIfAbsent(code,key -> new java.util.concurrent.atomic.LongAdder()).increment();";
        if(!service.contains(increment) || !controller.contains("Map.of(\"total\",total)")) throw new IllegalArgumentException("Daily analytics requires connected total analytics");
        service=service.replace(increment,"""
                if (targets.containsKey(code)) {
                            clicks.computeIfAbsent(code,key -> new java.util.concurrent.atomic.LongAdder()).increment();
                            String day=java.time.LocalDate.now(java.time.Clock.systemUTC()).toString();
                            daily.computeIfAbsent(code,key -> new java.util.concurrent.ConcurrentHashMap<>())
                                .computeIfAbsent(day,key -> new java.util.concurrent.atomic.LongAdder()).increment();
                        }
                """);
        int end=service.lastIndexOf('}');
        service=service.substring(0,end)+"""
                    private final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.LongAdder>> daily = new java.util.concurrent.ConcurrentHashMap<>();
                    public Map<String,Long> dailyClicks(String code) {
                        Map<String,Long> result=new java.util.TreeMap<>();
                        var counts=daily.get(code);
                        if(counts!=null) counts.forEach((day,count)->result.put(day,count.sum()));
                        return Map.copyOf(result);
                    }
                }
                """;
        controller=controller.replace("Map.of(\"total\",total)","Map.of(\"total\",total,\"daily\",service.dailyClicks(code))");
        controller=controller.replace("analytics(@PathVariable String code)","analytics(@PathVariable String code, @RequestParam(required=false) java.time.LocalDate day)")
                .replace("service.dailyClicks(code)","day==null ? service.dailyClicks(code) : Map.of(day.toString(),service.dailyClicks(code).getOrDefault(day.toString(),0L))");
        return Map.of(ROOT+"UrlService.java",service,ROOT+"UrlController.java",controller);
    }
    public static String analyticsTest(boolean daily) {
        return """
                package dev.ajaymatta.target;
                import java.net.URI;
                import java.net.http.*;
                import java.time.*;
                import java.util.Map;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.boot.test.web.server.LocalServerPort;
                import static org.assertj.core.api.Assertions.*;
                @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
                class %s {
                    @LocalServerPort int port;
                    final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
                    final ObjectMapper json=new ObjectMapper();
                    String create() throws Exception {
                        var response=client.send(HttpRequest.newBuilder(uri("/api/v1/urls")).header("Content-Type","application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("target","https://example.com/analytics")))).build(),HttpResponse.BodyHandlers.ofString());
                        assertThat(response.statusCode()).isEqualTo(201);
                        return json.readTree(response.body()).get("code").asText();
                    }
                    URI uri(String path) { return URI.create("http://localhost:"+port+path); }
                    HttpResponse<String> get(String path) throws Exception {
                        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),HttpResponse.BodyHandlers.ofString());
                    }
                    @Test void redirectsIncrementRealAnalytics() throws Exception {
                        String code=create();
                        for(int i=0;i<2;i++) {
                            var redirect=get("/"+code); assertThat(redirect.statusCode()).isEqualTo(302);
                            assertThat(redirect.headers().firstValue("Location")).contains("https://example.com/analytics");
                        }
                        var response=get("/api/v1/urls/"+code+"/analytics"); assertThat(response.statusCode()).isEqualTo(200);
                        var body=json.readTree(response.body());
                        %s
                    }
                    @Test void newCodeHasNoVisitsAndOtherCodesStayIndependent() throws Exception {
                        String first=create(), second=create(); get("/"+first);
                        var body=json.readTree(get("/api/v1/urls/"+second+"/analytics").body());
                        assertThat(body.get("total").asLong()).isZero();
                        %s
                    }
                    @Test void missingCodeHasNoAnalytics() throws Exception {
                        assertThat(get("/api/v1/urls/missing-code/analytics").statusCode()).isEqualTo(404);
                    }
                }
                """.formatted(daily ? "AnalyticsDailyTest" : "AnalyticsTotalTest",
                        daily ? "String utcDay=LocalDate.now(Clock.systemUTC()).toString(); assertThat(body.get(\"daily\").get(utcDay).asLong()).isEqualTo(2); assertThat(body.get(\"daily\").size()).isEqualTo(1); String yesterday=LocalDate.now(Clock.systemUTC()).minusDays(1).toString(); var prior=json.readTree(get(\"/api/v1/urls/\"+code+\"/analytics?day=\"+yesterday).body()); assertThat(prior.get(\"daily\").get(yesterday).asLong()).isZero(); assertThat(prior.get(\"daily\").size()).isEqualTo(1);"
                                : "assertThat(body.get(\"total\").asLong()).isEqualTo(2);",
                        daily ? "assertThat(body.get(\"daily\").size()).isZero();" : "assertThat(json.readTree(get(\"/api/v1/urls/\"+first+\"/analytics\").body()).get(\"total\").asLong()).isEqualTo(1);");
    }
    private static String required(Map<String,String> baseline,String path) {
        String value=baseline.get(path); if(value==null) throw new IllegalArgumentException("Unsupported brownfield layout"); return value;
    }
}
