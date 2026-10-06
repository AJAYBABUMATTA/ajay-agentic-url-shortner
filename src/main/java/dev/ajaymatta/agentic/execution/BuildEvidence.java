package dev.ajaymatta.agentic.execution;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Real process and report evidence; a zero exit code alone is insufficient for readiness. */
public record BuildEvidence(int exitCode, Duration duration, boolean timedOut,
                            String stdout, String stderr, List<String> compiledProductionPaths,
                            List<String> discoveredTests, List<String> failedTests,
                            Coverage coverage, FailureClassification classification) {
    public BuildEvidence {
        Objects.requireNonNull(duration);
        Objects.requireNonNull(stdout);
        Objects.requireNonNull(stderr);
        Objects.requireNonNull(coverage);
        Objects.requireNonNull(classification);
        if (duration.isNegative()) throw new IllegalArgumentException("Negative duration");
        compiledProductionPaths = List.copyOf(compiledProductionPaths);
        discoveredTests = List.copyOf(discoveredTests);
        failedTests = List.copyOf(failedTests);
        if (!discoveredTests.containsAll(failedTests)) throw new IllegalArgumentException("Failure not in discovered tests");
        if (classification == FailureClassification.NONE && (exitCode != 0 || timedOut || !failedTests.isEmpty())) {
            throw new IllegalArgumentException("Failure evidence classified as success");
        }
    }

    public record Coverage(boolean available, long coveredLines, long missedLines, String reportLocation) {
        public Coverage {
            if (coveredLines < 0 || missedLines < 0 || (available && (reportLocation == null || reportLocation.isBlank()))
                    || (!available && (coveredLines != 0 || missedLines != 0))) {
                throw new IllegalArgumentException("Invalid coverage evidence");
            }
        }
    }

    public enum FailureClassification { NONE, COMPILATION, TEST, POLICY, DEPENDENCY, INFRASTRUCTURE, TIMEOUT, UNKNOWN }
}
