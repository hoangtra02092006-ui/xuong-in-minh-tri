package vn.minhtri.xuongin.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.minhtri.xuongin.file.ContentBox;
import vn.minhtri.xuongin.file.FileType;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

class DiskFileStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    @TempDir
    Path root;

    @Test
    void writesUnderUploadsAndFindsMetadata() throws IOException {
        DiskFileStore store = new DiskFileStore(root);
        StoredFile file = put(store, "a", NOW);

        Path path = store.pathOf(file);
        assertThat(path).isEqualTo(root.toAbsolutePath().normalize().resolve("uploads").resolve("a.pdf"));
        assertThat(Files.readString(path)).isEqualTo("data-a");
        assertThat(store.find("a")).contains(file);
        assertThat(store.require("a")).isSameAs(file);
        assertThat(store.directory()).isEqualTo(path.getParent());
        assertThat(store.root()).isEqualTo(root.toAbsolutePath().normalize());
    }

    @Test
    void unknownIdIsFileNotFound() {
        DiskFileStore store = new DiskFileStore(root);

        assertThatThrownBy(() -> store.require("nope"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.FILE_NOT_FOUND));
        assertThat(store.find("nope")).isEmpty();
    }

    @Test
    void storedPathCannotEscapeTheUploadFolder() {
        DiskFileStore store = new DiskFileStore(root);

        assertThatThrownBy(() -> store.resolve(".." + root.getFileSystem().getSeparator() + "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void previewIsCachedOnlyForKnownFiles() throws IOException {
        DiskFileStore store = new DiskFileStore(root);
        put(store, "a", NOW);

        store.savePreview("a", new byte[] {1});
        store.savePreview("ghost", new byte[] {2});

        assertThat(store.findPreview("a")).isPresent();
        assertThat(store.findPreview("ghost")).isEmpty();
    }

    @Test
    void deleteRemovesDiskMetadataAndPreview() throws IOException {
        DiskFileStore store = new DiskFileStore(root);
        StoredFile file = put(store, "a", NOW);
        store.savePreview("a", new byte[] {1});

        store.delete("a");
        store.delete("unknown");

        assertThat(Files.exists(store.pathOf(file))).isFalse();
        assertThat(store.find("a")).isEmpty();
        assertThat(store.findPreview("a")).isEmpty();
    }

    @Test
    void purgeRemovesOldFilesFromDiskMapAndPreviewCacheAndKeepsNewOnes() throws IOException {
        DiskFileStore store = new DiskFileStore(root);
        StoredFile old = put(store, "old", NOW.minusSeconds(49 * 3600));
        StoredFile fresh = put(store, "fresh", NOW.minusSeconds(3600));
        store.savePreview("old", new byte[] {1});
        store.savePreview("fresh", new byte[] {2});

        int removed = store.purgeOlderThan(NOW.minusSeconds(48 * 3600));

        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(store.pathOf(old))).isFalse();
        assertThat(store.find("old")).isEmpty();
        assertThat(store.findPreview("old")).isEmpty();
        assertThat(Files.exists(store.pathOf(fresh))).isTrue();
        assertThat(store.find("fresh")).isPresent();
        assertThat(store.findPreview("fresh")).isPresent();
    }

    @Test
    void purgeRemovesOldOrphanFilesLeftByARestart() throws IOException {
        DiskFileStore store = new DiskFileStore(root);
        put(store, "live", NOW);
        Path orphanOld = store.directory().resolve("lost-old.pdf");
        Path orphanNew = store.directory().resolve("lost-new.pdf");
        Files.writeString(orphanOld, "x");
        Files.writeString(orphanNew, "x");
        Files.setLastModifiedTime(orphanOld, FileTime.from(NOW.minusSeconds(100 * 3600)));
        Files.setLastModifiedTime(orphanNew, FileTime.from(NOW));
        Files.setLastModifiedTime(store.directory().resolve("live.pdf"), FileTime.from(NOW.minusSeconds(100 * 3600)));

        int removed = store.purgeOlderThan(NOW.minusSeconds(48 * 3600));

        assertThat(removed).isEqualTo(1);
        assertThat(orphanOld).doesNotExist();
        assertThat(orphanNew).exists();
        // A live file is judged by its upload time, not its disk timestamp.
        assertThat(store.directory().resolve("live.pdf")).exists();
    }

    @Test
    void purgeOnEmptyStoreDoesNothing() {
        assertThat(new DiskFileStore(root).purgeOlderThan(NOW)).isZero();
    }

    @Test
    void deleteContentToleratesMissingFile() {
        new DiskFileStore(root).deleteContent("never-written.pdf");
    }

    private static StoredFile put(DiskFileStore store, String id, Instant uploadedAt) throws IOException {
        String name = store.writeContent(id, ".pdf", new ByteArrayInputStream(("data-" + id).getBytes()));
        StoredFile file = new StoredFile(id, id + ".pdf", name, FileType.PDF, 10, 10, 10, 10,
                new ContentBox(0, 0, 28.35, 28.35), null, 0, 1, true, 6, uploadedAt);
        store.save(file);
        return file;
    }
}
