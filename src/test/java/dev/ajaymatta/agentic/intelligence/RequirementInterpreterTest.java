package dev.ajaymatta.agentic.intelligence;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RequirementInterpreterTest {
    private final RequirementInterpreter interpreter = new RequirementInterpreter();
    @Test void distinctRequirementsProduceMeasurableDistinctCriteria() {
        var base = interpreter.interpret("Create a URL-shortener with HTTP 302 redirects", Map.of());
        var analytics = interpreter.interpret("Add total and UTC daily analytics to the URL-shortener", Map.of());
        assertThat(base.resolved()).isTrue(); assertThat(analytics.resolved()).isTrue();
        assertThat(base.criteria()).extracting(RequirementAnalysis.Criterion::capability).containsExactly("create", "redirect");
        assertThat(analytics.criteria()).extracting(RequirementAnalysis.Criterion::capability).containsExactly("analytics-total", "analytics-daily");
        assertThat(base.criteria()).allMatch(c -> c.behavioral() && c.description().contains("HTTP"));
    }
    @ParameterizedTest @ValueSource(strings={"Make it better", "Improve URL-shortener performance", "Add analytics to the URL-shortener", "Expire short links", "Build a blog platform"})
    void vagueMissingOrUnsupportedIntentRequiresClarification(String requirement) {
        assertThat(interpreter.interpret(requirement, Map.of()).questions()).isNotEmpty();
    }
    @Test void expiryAnswerMustActuallyResolveUnits() {
        String requirement = "Create a URL-shortener with expiry";
        assertThat(interpreter.interpret(requirement, Map.of("Q-EXPIRY", "soon")).resolved()).isFalse();
        assertThat(interpreter.interpret(requirement, Map.of("Q-EXPIRY", "24 hours")).resolved()).isTrue();
    }
    @Test void conflictingRedirectStatusesNeedOneExplicitChoice() {
        String requirement = "Create a URL-shortener that redirects with both 301 and 302";
        assertThat(interpreter.interpret(requirement, Map.of()).questions()).extracting(RequirementAnalysis.Question::id).contains("Q-REDIRECT");
        var clarified = interpreter.interpret(requirement, Map.of("Q-REDIRECT", "301"));
        assertThat(clarified.resolved()).isTrue();
        assertThat(clarified.criteria()).anyMatch(c -> c.capability().equals("redirect") && c.description().contains("301"));
    }
    @Test void performanceNeedsThresholdPercentileAndLoad() {
        String requirement = "Make URL-shortener redirects faster";
        assertThat(interpreter.interpret(requirement, Map.of("Q-PERFORMANCE", "50 ms")).resolved()).isFalse();
        assertThat(interpreter.interpret(requirement, Map.of("Q-PERFORMANCE", "p95 under 50 ms at 100 requests per second in local test environment")).resolved()).isTrue();
    }
}
