package dev.ajaymatta.agentic.workflow.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Caller-owned inputs only. Execution state, outputs, evidence and approvals are not inputs. */
public record SubmitRequirement(
        @NotBlank @Size(max = 10000)
        @Schema(example = "Create a URL-shortener with HTTP 302 redirects") String requirement,
        @NotBlank @Size(max = 512)
        @Schema(example = "greenfield-url-shortener", description = "Repository selector; not accessed in foundation")
        String repositoryPath) {}
