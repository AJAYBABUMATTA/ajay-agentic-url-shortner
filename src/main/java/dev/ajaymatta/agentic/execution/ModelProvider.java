package dev.ajaymatta.agentic.execution;

import java.time.Duration;
import java.util.Objects;

/** Providers return schema-constrained content; they cannot invoke tools or complete tasks. */
public interface ModelProvider {
    ModelResponse generate(ModelRequest request);

    record ModelRequest(AgentRole role, String schemaVersion, ExecutionContext context) {
        public ModelRequest {
            Objects.requireNonNull(role);
            Objects.requireNonNull(context);
            if (schemaVersion == null || schemaVersion.isBlank()) throw new IllegalArgumentException("Schema missing");
        }
    }

    record ModelResponse(String provider, String model, String content, Duration duration) {
        public ModelResponse {
            if (provider == null || provider.isBlank() || model == null || model.isBlank()
                    || content == null || duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("Invalid provider response");
            }
        }
    }
}
