package vn.minhtri.xuongin.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.AppProperties;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.storage.FileStore;

/** Upload (store on disk, measure, keep metadata) and previews. */
@Service
public class FileService {

    static final List<String> PDF_EXTENSIONS = List.of(".pdf");
    static final List<String> IMAGE_EXTENSIONS = List.of(".png", ".jpg", ".jpeg");
    private static final long MB = 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileStore store;
    private final PdfMetadataReader pdfReader;
    private final ImageMetadataReader imageReader;
    private final PreviewRenderer previews;
    private final long maxFileSize;
    private final Clock clock;

    @Autowired
    public FileService(FileStore store, PdfMetadataReader pdfReader, ImageMetadataReader imageReader,
                       PreviewRenderer previews, AppProperties props) {
        this(store, pdfReader, imageReader, previews, props.storage().maxFileSize(), Clock.systemUTC());
    }

    FileService(FileStore store, PdfMetadataReader pdfReader, ImageMetadataReader imageReader,
                PreviewRenderer previews, long maxFileSize, Clock clock) {
        this.store = store;
        this.pdfReader = pdfReader;
        this.imageReader = imageReader;
        this.previews = previews;
        this.maxFileSize = maxFileSize;
        this.clock = clock;
    }

    /** A rendered preview and whether it is PNG (PDF) or JPEG (image). */
    public record Preview(byte[] bytes, boolean png) {}

    /** Validates every part first (type, size) so a bad file in the batch stores nothing. */
    public List<StoredFile> upload(List<MultipartFile> parts) {
        List<MultipartFile> files = parts == null ? List.of()
                : parts.stream().filter(p -> p != null).toList();
        if (files.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chưa chọn file nào để tải lên.");
        }
        files.forEach(this::validate);
        List<StoredFile> stored = new ArrayList<>(files.size());
        for (MultipartFile part : files) {
            stored.add(store(part));
        }
        return stored;
    }

    public Preview preview(String id) {
        StoredFile file = store.require(id);
        boolean png = file.type() == FileType.PDF;
        Optional<byte[]> cached = store.findPreview(id);
        if (cached.isPresent()) {
            return new Preview(cached.get(), png);
        }
        try {
            byte[] bytes = previews.render(file, store.pathOf(file));
            store.savePreview(id, bytes);
            return new Preview(bytes, png);
        } catch (IOException | RuntimeException e) {
            log.warn("Preview failed for file {}", id, e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không tạo được ảnh xem trước cho file này.", Map.of("fileId", id));
        }
    }

    private void validate(MultipartFile part) {
        String name = originalName(part);
        if (extension(name).isEmpty()) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FORMAT,
                    "File \"" + name + "\" không đúng định dạng. Chỉ nhận file PDF, PNG, JPG.",
                    Map.of("fileName", name, "allowed", List.of("pdf", "png", "jpg", "jpeg")));
        }
        if (part.getSize() > maxFileSize) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "File \"" + name + "\" vượt quá dung lượng cho phép (tối đa " + maxFileSize / MB + " MB mỗi file).",
                    Map.of("fileName", name, "maxBytes", maxFileSize, "sizeBytes", part.getSize()));
        }
    }

    private StoredFile store(MultipartFile part) {
        String name = originalName(part);
        String ext = extension(name).orElseThrow();
        String id = UUID.randomUUID().toString();
        String storedPath;
        try (InputStream in = part.getInputStream()) {
            storedPath = store.writeContent(id, ext, in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write upload to disk", e);
        }
        Path path = store.resolve(storedPath);
        SourceMetadata meta;
        try {
            meta = PDF_EXTENSIONS.contains(ext) ? pdfReader.read(path) : imageReader.read(path);
        } catch (IOException | RuntimeException e) {
            store.deleteContent(storedPath);
            log.info("Could not read size of upload {}: {}", name, e.toString());
            throw new ApiException(ErrorCode.SIZE_UNREADABLE,
                    "Không đọc được kích thước thật của file \"" + name + "\". File có thể bị hỏng; "
                            + "hãy xuất lại file hoặc nhập kích thước bằng tay.",
                    Map.of("fileName", name));
        }
        StoredFile file = new StoredFile(id, name, storedPath, meta.type(),
                meta.widthMm(), meta.heightMm(), meta.sourceWidthMm(), meta.sourceHeightMm(),
                meta.contentBox(), meta.occupancy(), meta.pageRotation(), meta.pageCount(), meta.hasAlpha(),
                part.getSize(), clock.instant());
        store.save(file);
        log.info("Uploaded {} as {} ({} x {} mm, trimmed={})", name, id,
                Math.round(file.widthMm() * 100) / 100.0, Math.round(file.heightMm() * 100) / 100.0, file.trimmed());
        return file;
    }

    /** File name without any client path (some browsers send "C:\\...\\name.pdf"). */
    static String originalName(MultipartFile part) {
        String raw = part.getOriginalFilename();
        if (raw == null || raw.isBlank()) {
            return "file";
        }
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        return name.isEmpty() ? "file" : name;
    }

    /** Lower-case supported extension with the dot, or empty when unsupported. */
    static Optional<String> extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        return PDF_EXTENSIONS.contains(ext) || IMAGE_EXTENSIONS.contains(ext) ? Optional.of(ext) : Optional.empty();
    }
}
