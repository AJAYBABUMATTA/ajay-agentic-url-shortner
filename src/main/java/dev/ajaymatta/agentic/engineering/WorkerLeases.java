package dev.ajaymatta.agentic.engineering;

import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL leases report durable ownership; shared-volume OS locks prevent stale concurrent writers. */
@Component
public class WorkerLeases {
    public record Owner(UUID revisionId,UUID workflowId,String ownerId,UUID token,String phase,Instant expiresAt,Instant closedAt) {}
    private final JdbcTemplate jdbc;
    private final Path locks;
    private final String instance;
    private final int seconds;
    private final ScheduledExecutorService heartbeats=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("lease-heartbeat").factory());
    public WorkerLeases(JdbcTemplate jdbc,@Value("${agentic.workspaces.root:agent-workspaces}") String root,@Value("${agentic.instance-id:local-worker}") String instance,@Value("${agentic.lease-seconds:15}") int seconds) {
        this.jdbc=jdbc; this.locks=Path.of(root).toAbsolutePath().normalize().resolve(".locks");
        if(!instance.matches("[A-Za-z0-9_-]{1,128}") || seconds<6 || seconds>120) throw new IllegalArgumentException("Invalid worker lease configuration");
        this.instance=instance; this.seconds=seconds;
    }
    public Optional<Lease> acquire(UUID workflow,UUID revision,String phase,boolean recovery) {
        FileChannel channel=null; FileLock lock=null;
        try {
            Files.createDirectories(locks);
            if(Files.isSymbolicLink(locks)) throw new IllegalArgumentException("Lease root cannot be a symlink");
            Path path=locks.resolve(revision+".lock");
            if(Files.isSymbolicLink(path)) throw new IllegalArgumentException("Lease lock cannot be a symlink");
            channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            try { lock=channel.tryLock(); } catch(OverlappingFileLockException busy) { channel.close(); return Optional.empty(); }
            if(lock==null) { channel.close(); return Optional.empty(); }
            UUID token=UUID.randomUUID();
            OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC), expiry=now.plusSeconds(seconds);
            int updated=jdbc.update("UPDATE worker_leases SET owner_id=?,token=?,phase=?,heartbeat_at=?,expires_at=?,closed_at=NULL WHERE revision_id=? AND (closed_at IS NOT NULL OR expires_at<?)",instance,token,phase,now,expiry,revision,now);
            if(updated==0 && jdbc.queryForObject("SELECT COUNT(*) FROM worker_leases WHERE revision_id=?",Integer.class,revision)==0 && !recovery) {
                jdbc.update("INSERT INTO worker_leases VALUES (?,?,?,?,?,?,?,NULL)",revision,workflow,instance,token,phase,now,expiry); updated=1;
            }
            if(updated==0) { lock.close(); channel.close(); return Optional.empty(); }
            return Optional.of(new Lease(revision,token,channel,lock));
        } catch(Exception failure) {
            try { if(lock!=null) lock.close(); if(channel!=null) channel.close(); } catch(Exception ignored) {}
            throw new IllegalStateException("Worker ownership unavailable",failure);
        }
    }
    public List<Owner> expired() { return jdbc.query("SELECT l.* FROM worker_leases l JOIN workflows w ON w.id=l.workflow_id WHERE w.current_revision=(SELECT revision_number FROM workflow_revisions WHERE id=l.revision_id) AND (w.status IN ('INTERPRETING','PLANNING') OR (w.status='EXECUTING' AND EXISTS (SELECT 1 FROM engineering_runs e WHERE e.revision_id=l.revision_id AND e.state='RUNNING'))) AND (l.closed_at IS NOT NULL OR l.expires_at<?)",(r,n)->owner(r),OffsetDateTime.now(ZoneOffset.UTC)); }
    public List<Owner> owners(UUID workflow) { return jdbc.query("SELECT * FROM worker_leases WHERE workflow_id=?",(r,n)->owner(r),workflow); }
    private Owner owner(java.sql.ResultSet r) throws java.sql.SQLException {
        var closed=r.getObject("closed_at",OffsetDateTime.class);
        return new Owner(r.getObject("revision_id",UUID.class),r.getObject("workflow_id",UUID.class),r.getString("owner_id"),r.getObject("token",UUID.class),r.getString("phase"),r.getObject("expires_at",OffsetDateTime.class).toInstant(),closed==null ? null : closed.toInstant());
    }
    public final class Lease implements AutoCloseable {
        private final UUID revision,token; private final FileChannel channel; private final FileLock lock; private final ScheduledFuture<?> heartbeat;
        Lease(UUID revision,UUID token,FileChannel channel,FileLock lock) {
            this.revision=revision; this.token=token; this.channel=channel; this.lock=lock;
            heartbeat=heartbeats.scheduleWithFixedDelay(()-> {
                try { var now=OffsetDateTime.now(ZoneOffset.UTC); jdbc.update("UPDATE worker_leases SET heartbeat_at=?,expires_at=? WHERE revision_id=? AND token=? AND closed_at IS NULL",now,now.plusSeconds(seconds),revision,token); }
                catch(RuntimeException unavailable) { /* The physical lock remains held: peers cannot write or recover concurrently. */ }
            },seconds/3,seconds/3,TimeUnit.SECONDS);
        }
        @Override public void close() {
            heartbeat.cancel(false);
            try { jdbc.update("UPDATE worker_leases SET closed_at=? WHERE revision_id=? AND token=?",OffsetDateTime.now(ZoneOffset.UTC),revision,token); }
            finally { try { lock.close(); channel.close(); } catch(Exception failure) { throw new IllegalStateException("Worker lock release failed",failure); } }
        }
    }
    @PreDestroy public void shutdown() { heartbeats.shutdownNow(); }
}
