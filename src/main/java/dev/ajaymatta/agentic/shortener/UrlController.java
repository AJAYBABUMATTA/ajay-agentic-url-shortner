package dev.ajaymatta.agentic.shortener;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController
public class UrlController {
    public record CreateRequest(@NotBlank @Size(max=2048) String target,@Size(min=3,max=64) String alias,Instant expiresAt) {}
    public record CreatedUrl(String code,String target,String shortUrl,boolean active,Instant expiresAt,String managementToken) {}
    private final UrlService service;
    public UrlController(UrlService service) { this.service=service; }
    @PostMapping("/api/v1/urls") @Operation(summary="Create a persistent short URL; retain its management token")
    public ResponseEntity<CreatedUrl> create(@Valid @RequestBody CreateRequest request) {
        if(request.alias()!=null) require("alias"); if(request.expiresAt()!=null) require("expiry");
        var created=service.create(request.target(),request.alias(),request.expiresAt()); var link=created.link();
        return ResponseEntity.created(URI.create("/"+link.code())).body(new CreatedUrl(link.code(),link.target(),"/"+link.code(),true,link.expiresAt(),created.managementToken()));
    }
    @GetMapping("/{code}") @Operation(summary="Redirect and atomically count a successful visit")
    public ResponseEntity<Void> redirect(@PathVariable @Pattern(regexp="[A-Za-z0-9_-]{3,64}") String code) {
        require("redirect"); return ResponseEntity.status(UrlCapabilities.REDIRECT_STATUS).location(URI.create(service.redirect(code))).cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/api/v1/urls/{code}") @Operation(summary="Inspect a link without incrementing counters")
    public UrlService.Link inspect(@PathVariable String code) { require("inspect"); return service.inspect(code); }
    @DeleteMapping("/api/v1/urls/{code}") @Operation(summary="Deactivate using X-Link-Token; repeated authorized deletion is idempotent")
    public ResponseEntity<Void> deactivate(@PathVariable String code,@RequestHeader(value="X-Link-Token",required=false) String token) { require("deactivate"); service.deactivate(code,token); return ResponseEntity.noContent().build(); }
    @GetMapping("/api/v1/urls/{code}/analytics") @Operation(summary="Read total and UTC-day counts; optional ISO date selects a day")
    public UrlService.Analytics analytics(@PathVariable String code,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate day) {
        require("analytics-total"); if(day!=null) require("analytics-daily"); return service.analytics(code,day);
    }
    private static void require(String capability) { if(!UrlCapabilities.enabled(capability)) throw new UrlFailure(501,"Capability is outside this approved generated scope"); }
}
