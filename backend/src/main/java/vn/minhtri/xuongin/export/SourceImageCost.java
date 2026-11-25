package vn.minhtri.xuongin.export;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.file.FileType;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.raster.SourceImages;
import vn.minhtri.xuongin.storage.FileStore;

/**
 * Heap to decode the largest source image of a sheet (see {@link SourceImages}), added to every
 * raster estimate and reserved while the sheet PDF is composed from images.
 */
@Component
public class SourceImageCost {

    private final FileStore files;

    public SourceImageCost(FileStore files) {
        this.files = files;
    }

    /** Largest decode cost among the sheet's distinct source files; unknown files count 0. */
    public long of(Sheet sheet) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (Placement placement : sheet.placements()) {
            ids.add(placement.fileId());
        }
        long max = 0;
        for (String id : ids) {
            StoredFile file = files.find(id).orElse(null);
            if (file == null) {
                continue;
            }
            long bytes = file.type() == FileType.PDF
                    ? SourceImages.largestDecodeBytes(files.pathOf(file))
                    : SourceImages.imageFileBytes(files.pathOf(file), file.hasAlpha());
            max = Math.max(max, bytes);
        }
        return max;
    }
}
