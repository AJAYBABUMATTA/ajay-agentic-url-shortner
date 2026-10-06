package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.workflow.api.SubmitRequirement;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.intelligence.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class WorkerRecoveryTest {
    @TempDir static Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("agentic.repositories.root",()->temp.resolve("sources").toString()); p.add("agentic.workspaces.root",()->temp.resolve("workspaces").toString());
    }
    @Autowired WorkflowService workflows;
    @Autowired RequirementProcessor intelligence;
    @Autowired ChangeApprovalService approvals;
    @Autowired EngineeringStore engineering;
    @Autowired WorkerLeases leases;
    @Autowired WorkerRecovery recovery;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void fixture() throws Exception { Files.createDirectories(temp.resolve("sources/green")); Files.writeString(temp.resolve("sources/green/README.md"),"Original recovery fixture\n"); }
    @Test void expiredDatabaseLeaseCannotOverrideALiveWorkspaceLock() {
        var workflow=workflows.submit(new SubmitRequirement("Create URL-shortener","green"));
        UUID revision=workflow.revision().id();
        var peer=new WorkerLeases(jdbc,temp.resolve("workspaces").toString(),"peer",6);
        try(var original=leases.acquire(workflow.workflow().id(),revision,"INTELLIGENCE",false).orElseThrow()) {
            jdbc.update("UPDATE worker_leases SET expires_at=? WHERE revision_id=?",OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(30),revision);
            assertThat(peer.acquire(workflow.workflow().id(),revision,"RECOVERY",true)).isEmpty();
        }
        try(var takeover=peer.acquire(workflow.workflow().id(),revision,"INTELLIGENCE",false).orElseThrow()) {
            assertThat(leases.owners(workflow.workflow().id()).getFirst().ownerId()).isEqualTo("peer");
            assertThat(leases.acquire(workflow.workflow().id(),revision,"INTELLIGENCE",false)).isEmpty();
        } finally { peer.shutdown(); }
    }
    @Test void interruptedBuildRestoresBaselineAndCannotFabricateSuccess() throws Exception {
        var submitted=workflows.submit(new SubmitRequirement("Create URL-shortener","green")); UUID id=submitted.workflow().id(), revision=submitted.revision().id();
        intelligence.process(id); var planned=workflows.get(id);
        approvals.decide(id,new ChangeApprovalService.Request(1,planned.intelligence().planHash(),ChangeApprovalService.Decision.APPROVED,"Test fixture exact approval"),"fixture-reviewer");
        assertThat(leases.expired()).noneMatch(owner->owner.revisionId().equals(revision));
        assertThat(recovery.recover(leases.owners(id).getFirst())).isFalse();
        assertThat(engineering.claim(revision)).isTrue();
        UUID task=planned.tasks().stream().filter(t->t.key().equals("validate-build")).findFirst().orElseThrow().id(), attempt=UUID.randomUUID();
        var now=OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(20);
        jdbc.update("UPDATE agent_tasks SET state='RUNNING' WHERE id=?",task);
        jdbc.update("INSERT INTO execution_attempts VALUES (?,?,?,?,?,?,?,?,?,?)",attempt,revision,task,1,"interrupted-worker","RUNNING","[]",now,null,null);
        Path repository=engineering.workspace(revision).repository(); Files.writeString(repository.resolve("README.md"),"Interrupted mutation\n");
        jdbc.update("UPDATE worker_leases SET phase='ENGINEERING',closed_at=NULL,expires_at=? WHERE revision_id=?",now,revision);
        var previous=leases.owners(id).getFirst();
        assertThat(recovery.recover(previous)).isTrue();
        assertThat(workflows.get(id).workflow().status().name()).isEqualTo("SAFE_STOPPED");
        assertThat(Files.readString(repository.resolve("README.md"))).isEqualTo("Original recovery fixture\n");
        assertThat(engineering.view(revision).outcome()).isNull();
        assertThat(engineering.view(revision).artifacts()).anyMatch(a->a.type()==dev.ajaymatta.agentic.execution.EngineeringArtifact.ArtifactType.BUILD_EVIDENCE && a.content().contains("INFRASTRUCTURE"));
        assertThat(jdbc.queryForObject("SELECT failure_classification FROM build_evidence WHERE attempt_id=?",String.class,attempt)).isEqualTo("INFRASTRUCTURE");
        assertThat(jdbc.queryForObject("SELECT discovered_tests FROM build_evidence WHERE attempt_id=?",String.class,attempt)).isEqualTo("[]");
        assertThat(engineering.rollbacks(revision).getLast().verified()).isTrue();
        assertThat(workflows.get(id).audit()).anyMatch(a->a.eventType().equals("WORKER_FAILOVER_RECOVERED"));
        assertThat(recovery.recover(previous)).isFalse();
    }
}
