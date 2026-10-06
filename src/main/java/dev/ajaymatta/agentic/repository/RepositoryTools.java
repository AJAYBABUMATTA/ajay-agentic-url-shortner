package dev.ajaymatta.agentic.repository;

import dev.ajaymatta.agentic.execution.Hashes;
import dev.ajaymatta.agentic.execution.RepositoryWorkspace;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded reads and snapshot copying only. No target-source write or command capability. */
@Component
public class RepositoryTools {
    private static final Set<String> EXCLUDED = Set.of(".git", ".idea", "target", "node_modules", ".env", ".aws", ".codex");
    private static final Set<String> EXTENSIONS = Set.of("java", "xml", "md", "yaml", "yml", "json", "properties", "txt", "ps1", "sh");
    private final Path approvedRoot;
    private final Path workspaceRoot;
    private final int maximumFiles;
    private final long maximumFileBytes;
    private final long maximumTotalBytes;

    public RepositoryTools(@Value("${agentic.repositories.root:./scenario-repositories}") String approvedRoot,
            @Value("${agentic.workspaces.root:./agent-workspaces}") String workspaceRoot,
            @Value("${agentic.repositories.max-files:2000}") int maximumFiles,
            @Value("${agentic.repositories.max-file-bytes:262144}") long maximumFileBytes,
            @Value("${agentic.repositories.max-total-bytes:10485760}") long maximumTotalBytes) {
        this.approvedRoot = Path.of(approvedRoot).toAbsolutePath().normalize();
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.maximumFiles = maximumFiles;
        this.maximumFileBytes = maximumFileBytes;
        this.maximumTotalBytes = maximumTotalBytes;
        if (maximumFiles < 1 || maximumFileBytes < 1 || maximumTotalBytes < 1
                || maximumFileBytes > 10485760 || maximumTotalBytes > 104857600 || maximumFiles > 10000
                || this.workspaceRoot.startsWith(this.approvedRoot) || this.approvedRoot.startsWith(this.workspaceRoot)) {
            throw new IllegalArgumentException("Repository and workspace roots must be separate with positive limits");
        }
    }

    public static void validateSelector(String selector) {
        if (selector == null || selector.isBlank() || !selector.matches("[a-zA-Z0-9_./-]+")
                || selector.startsWith("/") || Arrays.stream(selector.split("/", -1))
                .anyMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."))) {
            throw new RepositoryPolicyException("Repository selector must be a relative approved-root path");
        }
    }

    public RepositorySnapshot snapshot(String selector, UUID workflowId, UUID revisionId) {
        validateSelector(selector);
        Path revisionRoot = workspaceRoot.resolve(workflowId.toString()).resolve(revisionId.toString());
        try {
            ensureNoLinks(approvedRoot);
            Path root = approvedRoot.toRealPath();
            Path source = root.resolve(selector).normalize();
            checkContained(source, root);
            if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) throw new RepositoryPolicyException("Repository is unavailable");
            Map<String, String> contents = readTree(source);
            List<RepositorySnapshot.FileEntry> entries = contents.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> new RepositorySnapshot.FileEntry(entry.getKey(), Hashes.sha256(entry.getValue()),
                            entry.getValue().getBytes(StandardCharsets.UTF_8).length)).toList();
            String manifest = entries.stream().map(e -> e.path() + "\t" + e.bytes() + "\t" + e.sha256() + "\n")
                    .reduce("", String::concat);
            ensureNoLinks(workspaceRoot);
            Files.createDirectories(workspaceRoot);
            ensureNoLinks(workspaceRoot);
            ensureNoLinks(revisionRoot.getParent());
            Files.createDirectories(revisionRoot.getParent());
            ensureNoLinks(revisionRoot.getParent());
            Files.createDirectory(revisionRoot);
            Path baseline = Files.createDirectory(revisionRoot.resolve("baseline"));
            Path repository = Files.createDirectory(revisionRoot.resolve("repository"));
            for (var entry : contents.entrySet()) {
                copyFile(baseline, entry.getKey(), entry.getValue());
                copyFile(repository, entry.getKey(), entry.getValue());
            }
            if (!readTree(source).equals(contents) || !readTree(baseline).equals(contents) || !readTree(repository).equals(contents)) {
                throw new RepositoryPolicyException("Repository changed during snapshot creation");
            }
            return new RepositorySnapshot(new RepositoryWorkspace(UUID.randomUUID(), revisionId, repository, baseline,
                    Hashes.sha256(manifest)), entries, contents);
        } catch (IOException exception) {
            throw new RepositoryPolicyException("Repository snapshot failed safely", exception);
        }
    }

    public Map<String, String> readTree(Path repository) throws IOException {
        Path base = repository.toAbsolutePath().normalize();
        if (!base.startsWith(approvedRoot) && !base.startsWith(workspaceRoot)) {
            throw new RepositoryPolicyException("Read location is outside approved repository/workspace roots");
        }
        ensureNoLinks(base);
        Map<String, String> result = new TreeMap<>();
        long[] total = {0};
        Files.walkFileTree(base, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = dir.getFileName().toString();
                boolean buildOutput = name.equals("target") && (dir.getParent().equals(base)
                        || Files.isRegularFile(dir.getParent().resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS));
                if (!dir.equals(base) && (buildOutput || (!name.equals("target") && EXCLUDED.contains(name)))) return FileVisitResult.SKIP_SUBTREE;
                checkContained(dir, base);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (EXCLUDED.contains(file.getFileName().toString())) return FileVisitResult.CONTINUE;
                checkContained(file, base);
                if (!attrs.isRegularFile() || !supported(file.getFileName().toString())) throw new RepositoryPolicyException("Unsupported repository file");
                if (result.size() >= maximumFiles || attrs.size() > maximumFileBytes) throw new RepositoryPolicyException("Repository resource limit exceeded");
                byte[] data;
                try (var channel = Files.newByteChannel(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                    ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(Math.min(maximumFileBytes, attrs.size()) + 1));
                    while (channel.read(buffer) > 0) {
                        if (!buffer.hasRemaining()) throw new RepositoryPolicyException("Repository file grew beyond limit");
                    }
                    buffer.flip();
                    data = new byte[buffer.remaining()]; buffer.get(data);
                }
                checkContained(file, base);
                var after = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!Objects.equals(attrs.fileKey(), after.fileKey()) || attrs.size() != after.size()
                        || !attrs.lastModifiedTime().equals(after.lastModifiedTime())) throw new RepositoryPolicyException("File changed during read");
                total[0] += data.length;
                if (total[0] > maximumTotalBytes) throw new RepositoryPolicyException("Repository total size limit exceeded");
                String content = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
                result.put(base.relativize(file).toString().replace('\\', '/'), content);
                return FileVisitResult.CONTINUE;
            }
        });
        return Map.copyOf(result);
    }

    public List<String> search(RepositorySnapshot snapshot, String literal, int maximumResults) {
        if (literal == null || literal.isBlank() || literal.length() > 256 || maximumResults < 1 || maximumResults > 100) {
            throw new RepositoryPolicyException("Invalid bounded search request");
        }
        List<String> result = new ArrayList<>();
        snapshot.contents().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String[] lines = entry.getValue().split("\n", -1);
            for (int i = 0; i < lines.length && result.size() < maximumResults; i++) {
                if (lines[i].contains(literal)) result.add(entry.getKey() + ":" + (i + 1));
            }
        });
        return List.copyOf(result);
    }

    private static boolean supported(String name) {
        if (Set.of("mvnw", "mvnw.cmd", ".gitignore", ".gitattributes").contains(name)) return true;
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }
    private static void copyFile(Path root, String relative, String content) throws IOException {
        Path destination = root.resolve(relative).normalize();
        if (!destination.startsWith(root)) throw new RepositoryPolicyException("Snapshot destination escaped workspace");
        ensureNoLinks(destination.getParent());
        Files.createDirectories(destination.getParent());
        ensureNoLinks(destination.getParent());
        Files.writeString(destination, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static void checkContained(Path path, Path root) throws IOException {
        ensureNoLinks(path);
        if (!path.normalize().startsWith(root) || !path.toRealPath().startsWith(root.toRealPath())
                || !path.toRealPath().equals(path.toAbsolutePath().normalize())) {
            throw new RepositoryPolicyException("Repository link or root escape rejected");
        }
    }
    private static void ensureNoLinks(Path path) {
        for (Path current = path.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new RepositoryPolicyException("Symbolic links are not allowed");
            try {
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !current.toRealPath().equals(current)) {
                    throw new RepositoryPolicyException("Repository junction or canonical-path change rejected");
                }
            } catch (IOException failure) { throw new RepositoryPolicyException("Cannot verify filesystem location", failure); }
        }
    }
    public static class RepositoryPolicyException extends RuntimeException {
        public RepositoryPolicyException(String message) { super(message); }
        public RepositoryPolicyException(String message, Throwable cause) { super(message, cause); }
    }
}
