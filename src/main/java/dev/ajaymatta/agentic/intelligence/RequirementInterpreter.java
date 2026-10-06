package dev.ajaymatta.agentic.intelligence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Explicit offline capability interpreter. Unsupported intent never becomes a generic implementation task. */
@Component
public class RequirementInterpreter {
    private static final Pattern DURATION = Pattern.compile("\\b[1-9][0-9]*\\s*(milliseconds?|seconds?|minutes?|hours?|days?)\\b");
    private static final Pattern TARGET = Pattern.compile("url[ -]?shorten|shorten.*(?:url|link)|short links?|redirect|alias|analytics|expir|deactivat");

    public RequirementAnalysis interpret(String requirement, Map<String, String> answers) {
        String normalized = requirement.strip().replaceAll("\\s+", " ");
        String text = normalized.toLowerCase(Locale.ROOT);
        String resolvedText = (text + " " + String.join(" ", answers.values())).toLowerCase(Locale.ROOT);
        List<RequirementAnalysis.Question> questions = new ArrayList<>();
        Map<String, RequirementAnalysis.Criterion> criteria = new LinkedHashMap<>();
        if (!TARGET.matcher(resolvedText).find()) {
            question(questions, "Q-SCOPE", "scope", "Which URL-shortener behavior should be created or changed?");
        }
        boolean base = resolvedText.contains("create") && (resolvedText.contains("shorten") || resolvedText.contains("short link"));
        if (base) {
            criterion(criteria, "create", "POST /api/v1/urls accepts an HTTP(S) target and returns HTTP 201 with a usable short code");
            criterion(criteria, "redirect", "GET /{code} returns HTTP 302 with the stored target in Location; unknown codes return 404");
        }
        if (resolvedText.contains("redirect") && !base) {
            criterion(criteria, "redirect", "GET /{code} returns the specified redirect status and correct Location; unknown codes return 404");
        }
        if (resolvedText.contains("301") && !resolvedText.contains("302")) {
            criterion(criteria, "redirect", "GET /{code} returns HTTP 301 with the stored target in Location; unknown codes return 404");
        }
        if (resolvedText.contains("alias")) {
            criterion(criteria, "alias", "A requested custom alias is used as the code; duplicate aliases return HTTP 409");
        }
        if (resolvedText.contains("expir") || resolvedText.contains("ttl")) {
            String expiry = answers.getOrDefault("Q-EXPIRY", resolvedText).toLowerCase(Locale.ROOT);
            if (!DURATION.matcher(expiry).find() && !expiry.contains("expiresat") && !expiry.contains("expiry timestamp")) {
                question(questions, "Q-EXPIRY", "missing-policy", "Specify an expiry duration with units or a caller-provided expiresAt timestamp");
            }
            criterion(criteria, "expiry", "Apply expiry policy: " + expiry + "; expired codes return HTTP 410 and do not increment clicks");
        }
        if (resolvedText.contains("analytic") || resolvedText.contains("click count")) {
            String analytics = answers.getOrDefault("Q-ANALYTICS", resolvedText).toLowerCase(Locale.ROOT);
            if (!analytics.contains("total") && !analytics.contains("daily") && !analytics.contains("utc")) {
                question(questions, "Q-ANALYTICS", "missing-policy", "Specify total clicks, UTC daily clicks, or both for each code");
            }
            if (analytics.contains("total")) criterion(criteria, "analytics-total", "Successful redirects increment per-code total clicks exactly once; analytics returns the total");
            if (analytics.contains("daily") || analytics.contains("utc")) criterion(criteria, "analytics-daily", "Successful redirects increment per-code UTC daily counts; analytics returns the requested day");
        }
        if (resolvedText.contains("deactivat")) criterion(criteria, "deactivate", "Deactivated codes return HTTP 410 and do not increment clicks");
        if (resolvedText.contains("inspect")) criterion(criteria, "inspect", "Inspection returns target, active state and expiry without incrementing clicks");
        if (resolvedText.contains("rate limit")) {
            if (!Pattern.compile("\\b[1-9][0-9]*\\s*(requests?|calls?)").matcher(resolvedText).find()
                    || !DURATION.matcher(resolvedText).find()
                    || !Pattern.compile("\\b(client|ip|user|token)\\b|api key").matcher(resolvedText).find()) {
                question(questions, "Q-RATE", "missing-policy", "Specify request count, interval with units and rate-limit identity");
            }
            criterion(criteria, "rate-limit", "Excess requests return HTTP 429 with Retry-After under the specified identity and window");
        }
        String performance = answers.getOrDefault("Q-PERFORMANCE", resolvedText).toLowerCase(Locale.ROOT);
        if (Pattern.compile("\\b(faster|fast|performance|latency|scalable)\\b").matcher(text).find()
                && (!Pattern.compile("\\b[1-9][0-9]*\\s*(ms|milliseconds?|seconds?)\\b").matcher(performance).find()
                    || !Pattern.compile("p(?:50|90|95|99)|percentile").matcher(performance).find()
                    || !Pattern.compile("requests? per second|rps|concurrent|load").matcher(performance).find())) {
            question(questions, "Q-PERFORMANCE", "unmeasurable", "Specify latency threshold, percentile, load and measurement environment");
        }
        if (Pattern.compile("\\b(faster|fast|performance|latency|scalable)\\b").matcher(text).find()
                && questions.stream().noneMatch(q -> q.id().equals("Q-PERFORMANCE"))) {
            criterion(criteria, "performance", "Validate the requested performance target: " + performance);
        }
        if (text.contains("301") && text.contains("302")) {
            String answer = answers.getOrDefault("Q-REDIRECT", "");
            if (!(answer.strip().equals("301") || answer.strip().equals("302"))) {
                question(questions, "Q-REDIRECT", "conflict", "Choose one redirect status: HTTP 301 or HTTP 302");
            } else {
                criterion(criteria, "redirect", "GET /{code} returns HTTP " + answer.strip() + " with the correct Location");
            }
        }
        if (Pattern.compile("\\b(no|without|disable)\\s+(?:click )?analytics\\b").matcher(text).find()
                && Pattern.compile("\\b(add|enable|with)\\s+(?:click )?analytics\\b").matcher(text).find()) {
            question(questions, "Q-CONFLICT", "conflict", "Resolve contradictory analytics instructions with a new requirement");
        }
        if (criteria.isEmpty() && questions.isEmpty()) question(questions, "Q-SCOPE", "unmeasurable", "Describe observable API behavior and acceptance conditions");
        return new RequirementAnalysis(normalized, List.copyOf(criteria.values()), questions,
                List.of("HTTP 302 is the default redirect unless explicitly overridden", "Existing unrelated behavior remains compatible"),
                List.of("Static repository reasoning requires generated integration tests to establish runtime connectivity",
                        "The deterministic provider supports documented URL-shortener capabilities; arbitrary domains require clarification"),
                List.of("Java 21", "Controlled isolated workspace", "No source mutation before clarification and change approval"));
    }

    private static void criterion(Map<String, RequirementAnalysis.Criterion> result, String capability, String description) {
        String id = "AC-" + capability.toUpperCase(Locale.ROOT);
        result.put(id, new RequirementAnalysis.Criterion(id, capability, description, true));
    }
    private static void question(List<RequirementAnalysis.Question> result, String id, String category, String text) {
        if (result.stream().noneMatch(q -> q.id().equals(id))) result.add(new RequirementAnalysis.Question(id, category, text));
    }
}
