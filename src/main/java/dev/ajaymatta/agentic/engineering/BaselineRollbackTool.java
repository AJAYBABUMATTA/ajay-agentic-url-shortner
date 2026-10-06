package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.repository.RepositoryTools;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BaselineRollbackTool implements EngineeringTool<List<String>,RollbackAction> {
    private final RepositoryTools repositories;
    private final ProposalTool proposals;
    public BaselineRollbackTool(RepositoryTools repositories,ProposalTool proposals) { this.repositories=repositories; this.proposals=proposals; }
    @Override public Capability capability() { return Capability.RESTORE_BASELINE; }
    @Override public RollbackAction execute(RepositoryWorkspace workspace,List<String> criteria) {
        try {
            var baseline=repositories.readTree(workspace.baseline());
            if(!ProposalTool.manifest(baseline).equals(workspace.baselineManifestHash())) throw new IllegalStateException("Baseline integrity failed");
            var current=proposals.read(workspace); var paths=new TreeSet<>(current.keySet()); paths.addAll(baseline.keySet());
            var operations=new ArrayList<FileOperation>();
            for(String path:paths) {
                String original=baseline.get(path), changed=current.get(path);
                if(Objects.equals(original,changed)) continue;
                operations.add(new FileOperation(original==null ? FileOperation.Operation.DELETE : changed==null ? FileOperation.Operation.CREATE : FileOperation.Operation.UPDATE,
                        path,original,changed==null ? null : Hashes.sha256(changed),"Restore immutable workflow baseline",workspace.revisionId().toString(),criteria,UUID.randomUUID(),List.of(workspace.baselineManifestHash())));
            }
            if(!operations.isEmpty()) proposals.execute(workspace,operations);
            String restored=ProposalTool.manifest(proposals.read(workspace));
            return new RollbackAction(UUID.randomUUID(),workspace.revisionId(),"Governed baseline restoration",workspace.baselineManifestHash(),restored,restored.equals(workspace.baselineManifestHash()),java.time.Instant.now());
        } catch(java.io.IOException failure) { throw new IllegalStateException("Baseline cannot be verified",failure); }
    }
}
