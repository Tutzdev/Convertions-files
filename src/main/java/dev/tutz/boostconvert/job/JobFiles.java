package dev.tutz.boostconvert.job;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload and result of each job, in {@code <storage-dir>/jobs/<job id>/}. Only directories named
 * after a UUID are ever deleted, so a misconfigured storage dir cannot wipe unrelated files.
 */
@Component
public class JobFiles {

    private static final Logger log = LoggerFactory.getLogger(JobFiles.class);
    private static final String INPUT = "input";
    private static final String RESULT = "result";

    private final Path root;

    public JobFiles(JobProperties properties) throws IOException {
        this.root = Files.createDirectories(properties.storageDir().resolve("jobs").toAbsolutePath().normalize());
        // Job records are in memory, so files left by a previous run belong to nobody.
        storedJobIds().forEach(this::delete);
    }

    public Path saveInput(UUID id, MultipartFile upload) throws IOException {
        Path input = Files.createDirectories(directory(id)).resolve(INPUT);
        upload.transferTo(input);
        return input;
    }

    public OutputStream openResult(UUID id) throws IOException {
        return new BufferedOutputStream(Files.newOutputStream(result(id)));
    }

    public Path result(UUID id) {
        return directory(id).resolve(RESULT);
    }

    public long resultSize(UUID id) throws IOException {
        return Files.size(result(id));
    }

    public void deleteInput(UUID id) {
        deleteQuietly(directory(id).resolve(INPUT));
    }

    public void deleteResult(UUID id) {
        deleteQuietly(result(id));
    }

    public void delete(UUID id) {
        deleteInput(id);
        deleteResult(id);
        deleteQuietly(directory(id));
    }

    public List<UUID> storedJobIds() {
        try (Stream<Path> directories = Files.list(root)) {
            return directories.map(path -> parseId(path.getFileName().toString()))
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Instant lastModified(UUID id) {
        try {
            return Files.getLastModifiedTime(directory(id)).toInstant();
        } catch (IOException gone) {
            return Instant.EPOCH;
        }
    }

    private Path directory(UUID id) {
        return root.resolve(id.toString());
    }

    private static Optional<UUID> parseId(String name) {
        try {
            return Optional.of(UUID.fromString(name));
        } catch (IllegalArgumentException notAJob) {
            return Optional.empty();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // The cleanup job retries orphaned directories later.
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }
}
