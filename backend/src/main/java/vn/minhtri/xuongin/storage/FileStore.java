package vn.minhtri.xuongin.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/**
 * Uploaded source files: bytes on disk, metadata (and the preview cache) in memory.
 * Interface so it can move to a database later.
 *
 * <p>Other modules read a source file with {@code store.require(id)} then {@code store.pathOf(file)}.
 */
public interface FileStore {

    String NOT_FOUND_MESSAGE = "Không tìm thấy file. File có thể đã bị dọn sau thời hạn lưu "
            + "hoặc máy chủ đã khởi động lại. Hãy tải file lên lại.";

    /** Copies {@code content} to disk as {@code <id><extension>}; returns the stored name for {@link StoredFile#storedPath()}. */
    String writeContent(String id, String extension, InputStream content) throws IOException;

    /** Absolute path of a stored name returned by {@link #writeContent}. */
    Path resolve(String storedPath);

    default Path pathOf(StoredFile file) {
        return resolve(file.storedPath());
    }

    void save(StoredFile file);

    Optional<StoredFile> find(String id);

    /** Like {@link #find} but throws {@code FILE_NOT_FOUND} (404) when missing. */
    default StoredFile require(String id) {
        return find(id).orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND, NOT_FOUND_MESSAGE, Map.of("fileId", id)));
    }

    /** Removes bytes on disk, metadata and cached preview. Unknown id is a no-op. */
    void delete(String id);

    /** Deletes a written file that never got metadata (upload failed while reading it). */
    void deleteContent(String storedPath);

    Optional<byte[]> findPreview(String id);

    /** Caches a preview; ignored when the file is unknown (already purged). */
    void savePreview(String id, byte[] preview);

    /**
     * Removes files uploaded before {@code cutoff} from all three places: disk, metadata map and preview cache.
     * Orphan files on disk (metadata lost after a restart) older than the cutoff are removed too.
     *
     * @return number of files deleted from disk
     */
    int purgeOlderThan(Instant cutoff);
}
