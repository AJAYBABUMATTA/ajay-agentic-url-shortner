package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RecoveryPolicy implements RetryPolicy,FallbackStrategy {
    @Override public RetryPolicy.Decision decide(ExecutionAttempt attempt,BuildEvidence.FailureClassification classification) {
        boolean retry=attempt.number()<3 && (classification==BuildEvidence.FailureClassification.COMPILATION || classification==BuildEvidence.FailureClassification.TEST);
        return new RetryPolicy.Decision(retry,3,Duration.ofMillis(250),retry ? "Retry only after a validated scoped production repair" : "Bound exhausted or infrastructure/policy failure requires safe stop");
    }
    @Override public FallbackStrategy.Decision decide(ExecutionContext context,BuildEvidence failure) {
        return new FallbackStrategy.Decision(failure.classification()==BuildEvidence.FailureClassification.COMPILATION || failure.classification()==BuildEvidence.FailureClassification.TEST
                ? FallbackStrategy.Action.REPAIR : FallbackStrategy.Action.HUMAN_INTERVENTION,
                "Compiler/test evidence may permit a scoped repair; unavailable tools, dependencies, timeout and unknown failures are not blindly retried");
    }
}
