package vn.minhtri.xuongin.file;

import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload and preview endpoints (spec 6.1, 6.2). */
@RestController
@RequestMapping(FileController.BASE_PATH)
public class FileController {

    public static final String BASE_PATH = "/api/v1/files";

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    /** Multipart field {@code files}, repeatable. A missing field is answered with INVALID_REQUEST, not a 500. */
    @PostMapping
    public List<FileResponse> upload(@RequestParam(name = "files", required = false) List<MultipartFile> parts) {
        return files.upload(parts).stream().map(FileResponse::from).toList();
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String id) {
        FileService.Preview preview = files.preview(id);
        return ResponseEntity.ok()
                .contentType(preview.png() ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(preview.bytes());
    }
}
