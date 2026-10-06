package dev.ajaymatta.target;

import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class UrlController {
    private final UrlService service;
    public UrlController(UrlService service) { this.service = service; }
    @PostMapping("/api/v1/urls")
    public ResponseEntity<Map<String,String>> create(@RequestBody Map<String,String> request) {
        String target = request.get("target");
        if (target == null || !(target.startsWith("https://") || target.startsWith("http://"))) return ResponseEntity.badRequest().build();
        return ResponseEntity.status(201).body(Map.of("code", service.create(target)));
    }
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = service.resolve(code);
        return target == null ? ResponseEntity.notFound().build() : ResponseEntity.status(302).location(URI.create(target)).build();
    }
}
