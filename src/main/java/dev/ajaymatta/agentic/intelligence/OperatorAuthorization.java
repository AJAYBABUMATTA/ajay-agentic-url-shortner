package dev.ajaymatta.agentic.intelligence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OperatorAuthorization {
    private final byte[] expected;
    public OperatorAuthorization(@Value("${agentic.operator.token:}") String token) { expected = token.getBytes(StandardCharsets.UTF_8); }
    public String authenticate(String actor, String token) {
        if (expected.length == 0) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Operator authorization is not configured");
        if (actor == null || actor.isBlank() || actor.length() > 160 || token == null || token.length() > 512
                || !MessageDigest.isEqual(expected, token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Operator authentication required");
        }
        return actor.strip();
    }
}
