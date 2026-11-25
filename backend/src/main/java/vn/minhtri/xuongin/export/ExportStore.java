package vn.minhtri.xuongin.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.export.pdf.PdfComposer;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.job.JobStore;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.AppProperties;
import vn.minhtri.xuongin.storage.FileStore;
import vn.minhtri.xuongin.storage.RetentionParticipant;

/**
 * Built files of a job on disk, never in RAM (constraints, memory): {@code <storage>/exports/<jobId>/}
 * holds {@code tam-NN.pdf} (sheet PDF without cut lines), {@code tam-NN-vien.pdf} (with the grey
 * cut lines, only when the job asked for them), {@code tam-NN.tif} and {@code tam-NN-cat.pdf}. Each file is written to a temp
 * name and moved into place, so a reader never sees half a file. The folder is removed with its
 * job by the retention sweep.
 */
@Component
public class ExportStore implements RetentionParticipant {

    static final String EXPORT_DIR = "exports";
    private static final Logger log = LoggerFactory.getLogger(ExportStore.class);

    private final Path root;
    private final JobStore jobs;
    private final FileStore files;
    private final PdfComposer composer;
    private final Map<Path, Object> locks = new ConcurrentHashMap<>();
    private final List<Consumer<String>> purgeListeners = new ArrayList<>();

    @Autowired
    public ExportStore(AppProperties props, JobStore jobs, FileStore files, PdfComposer composer) {
        this(Path.of(props.storage().path()), jobs, files, composer);
    }

    ExportStore(Path storageRoot, JobStore jobs, FileStore files, PdfComposer composer) {
        this.root = storageRoot.toAbsolutePath().normalize().resolve(EXPORT_DIR);
        this.jobs = jobs;
        this.files = files;
        this.composer = composer;
    }

    /** Called with the jobId of every export folder the sweep removes (drops in-memory state). */
    public synchronized void onPurge(Consumer<String> listener) {
        purgeListeners.add(listener);
    }

    public Path root() {
        return root;
    }

    public Path jobDir(String jobId) {
        Path dir = root.resolve(jobId).normalize();
        if (!dir.getParent().equals(root)) {
            throw new IllegalArgumentException("Job id escapes the export folder: " + jobId);
        }
        return dir;
    }

    /** {@code tam-01.tif} etc. for sheet index 0. */
    public Path sheetFile(String jobId, int index, String suffix) {
        return jobDir(jobId).resolve(String.format("tam-%02d%s", index + 1, suffix));
    }

    public Path tifPath(String jobId, int index) {
        return sheetFile(jobId, index, ".tif");
    }

    /** Print PDF with the job's own cut-line choice: what is downloaded and what the TIF rasterizes. */
    public Path printPdf(Job job, Sheet sheet) throws IOException {
        return pdf(job, sheet, job.request().drawCutLinesOrDefault());
    }

    /** Same layout without cut lines, the source of the cut file (spec 10.5). */
    public Path pdfWithoutCutLines(Job job, Sheet sheet) throws IOException {
        return pdf(job, sheet, false);
    }

    /** Where {@link #printPdf} puts the file (it may not exist yet). */
    public Path printPdfPath(Job job, Sheet sheet) {
        return pdfPath(job, sheet, job.request().drawCutLinesOrDefault());
    }

    private Path pdfPath(Job job, Sheet sheet, boolean cutLines) {
        return sheetFile(job.jobId(), sheet.index(), cutLines ? "-vien.pdf" : ".pdf");
    }

    private Path pdf(Job job, Sheet sheet, boolean cutLines) throws IOException {
        return ensure(pdfPath(job, sheet, cutLines), out ->
                composer.compose(sheet, id -> files.find(id).orElse(null), files::pathOf, cutLines, out));
    }

    /** Writes the file once; later calls return the existing file. Concurrent callers wait for the first. */
    public Path ensure(Path target, Writer writer) throws IOException {
        if (Files.exists(target)) {
            return target;
        }
        Object lock = locks.computeIfAbsent(target, p -> new Object());
        try {
            synchronized (lock) {
                if (!Files.exists(target)) {
                    write(target, writer);
                }
            }
        } finally {
            locks.remove(target, lock);
        }
        return target;
    }

    private static void write(Path target, Writer writer) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                writer.write(out);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Writes the content of one export file. */
    @FunctionalInterface
    public interface Writer {
        void write(OutputStream out) throws IOException;
    }

    /**
     * Removes the export folder of every job that is gone from the job store or older than the
     * cutoff (runs after the job sweep, so folders of jobs just purged go too).
     */
    @Override
    public int purgeOlderThan(Instant cutoff) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        List<String> removed = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path dir : dirs) {
                String jobId = dir.getFileName().toString();
                boolean orphan = jobs.find(jobId).isEmpty();
                boolean old = Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff);
                if (orphan || old) {
                    deleteTree(dir);
                    removed.add(jobId);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not sweep " + root, e);
        }
        List<Consumer<String>> listeners;
        synchronized (this) {
            listeners = List.copyOf(purgeListeners);
        }
        for (String jobId : removed) {
            listeners.forEach(l -> l.accept(jobId));
        }
        return removed.size();
    }

    /** Deletes a job's folder now (also used when a job is gone). */
    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete export file {}", path, e);
                }
            }
        }
    }
}
