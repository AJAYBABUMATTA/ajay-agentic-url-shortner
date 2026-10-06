package dev.ajaymatta.agentic.engineering;

import io.micrometer.core.instrument.*;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EngineeringMetrics {
    private final MeterRegistry registry;
    public EngineeringMetrics(MeterRegistry registry,JdbcTemplate jdbc) {
        this.registry=registry;
        Gauge.builder("agentic.release.success.rate",jdbc,db->{
            Double total=db.queryForObject("SELECT COUNT(*) FROM workflows WHERE status IN ('RELEASE_READY','SAFE_STOPPED','ROLLED_BACK','FAILED','CANCELLED')",Double.class);
            Double success=db.queryForObject("SELECT COUNT(*) FROM workflows WHERE status='RELEASE_READY'",Double.class);
            return total==null || total==0 ? 0 : success/total;
        }).register(registry);
    }
    public void outcome(String status,Duration duration) {
        registry.counter("agentic.workflows.outcomes","outcome",status).increment();
        registry.timer("agentic.workflow.duration","outcome",status).record(duration);
    }
    public void retry(String classification) { registry.counter("agentic.retries","classification",classification).increment(); }
    public void fallback(String action) { registry.counter("agentic.fallbacks","action",action).increment(); }
    public void rollback(boolean verified) { registry.counter("agentic.rollbacks","verified",Boolean.toString(verified)).increment(); }
    public void recovered(Duration duration) { registry.timer("agentic.recovery.duration").record(duration); }
    public void stage(String role,Duration duration) { registry.timer("agentic.agent.duration","role",role).record(duration); }
}
