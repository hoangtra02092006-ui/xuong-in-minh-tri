package vn.minhtri.xuongin.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.shared.AppProperties;

/**
 * Files under {@code <app.storage.path>/uploads}, metadata and previews in memory.
 * The {@code uploads} sub-folder keeps the orphan sweep away from files other modules write under the storage root.
 */
@Component
public class DiskFileStore implements FileStore {

    static final String UPLOAD_DIR = "uploads";
    private static final Logger log = LoggerFactory.getLogger(DiskFileStore.class);

    private final Path root;
    private final Path dir;
    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();
    private final Map<String, byte[]> previews = new ConcurrentHashMap<>();

    @Autowired
    public DiskFileStore(AppProperties props) {
        this(Path.of(props.storage().path()));
    }

    public DiskFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.dir = this.root.resolve(UPLOAD_DIR);
    }

    /** Storage root ({@code app.storage.path}), used for the free-disk check. */
    public Path root() {
        return root;
    }

    /** Folder holding the uploads (created on first write so runs that never upload leave no trace). */
    public Path directory() {
        return dir;
    }

    @Override
    public String writeContent(String id, String extension, InputStream content) throws IOException {
        Files.createDirectories(dir);
        String name = id + extension;
        Files.copy(content, resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    @Override
    public Path resolve(String storedPath) {
        Path path = dir.resolve(storedPath).normalize();
        if (!path.startsWith(dir) || path.equals(dir)) {
            throw new IllegalArgumentException("Stored path escapes the upload folder: " + storedPath);
        }
        return path;
    }

    @Override
    public void save(StoredFile file) {
        files.put(file.id(), file);
    }

    @Override
    public Optional<StoredFile> find(String id) {
        return Optional.ofNullable(files.get(id));
    }

    @Override
    public void delete(String id) {
        StoredFile removed = files.remove(id);
        previews.remove(id);
        if (removed != null) {
            deleteContent(removed.storedPath());
        }
    }

    @Override
    public void deleteContent(String storedPath) {
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", storedPath, e);
        }
    }

    @Override
    public Optional<byte[]> findPreview(String id) {
        return Optional.ofNullable(previews.get(id));
    }

    @Override
    public void savePreview(String id, byte[] preview) {
        if (files.containsKey(id)) {
            previews.put(id, preview);
        }
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        int deleted = 0;
        for (StoredFile file : files.values()) {
            if (file.uploadedAt().isBefore(cutoff)) {
                delete(file.id());
                deleted++;
            }
        }
        // A preview whose file is gone would never be freed (a forgotten map kept RAM high in the old app).
        previews.keySet().retainAll(files.keySet());
        return deleted + purgeOrphans(cutoff);
    }

    private int purgeOrphans(Instant cutoff) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        Set<String> live = files.values().stream().map(StoredFile::storedPath).collect(Collectors.toSet());
        int deleted = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (Files.isRegularFile(entry)
                        && !live.contains(entry.getFileName().toString())
                        && Files.getLastModifiedTime(entry).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(entry);
                    deleted++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not sweep " + dir, e);
        }
        return deleted;
    }
}
