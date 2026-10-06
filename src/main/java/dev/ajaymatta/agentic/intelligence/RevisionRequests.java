package dev.ajaymatta.agentic.intelligence;

import jakarta.validation.constraints.*;
import java.util.Map;

public final class RevisionRequests {
    private RevisionRequests() {}
    public record Clarification(@Min(1) int expectedRevision,
                                @NotEmpty @Size(max=20) Map<@NotBlank @Size(max=80) String, @NotBlank @Size(max=2000) String> answers) {}
    public record Replan(@Min(1) int expectedRevision, @NotBlank @Size(max=2000) String reason,
                         @Size(min=1, max=10000) String requirement) {}
}
