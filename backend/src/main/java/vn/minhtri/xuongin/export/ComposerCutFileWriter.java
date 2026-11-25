package vn.minhtri.xuongin.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.export.cut.CutComposer;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.storage.FileStore;

/** {@link CutFileWriter} backed by the cut module's {@link CutComposer} (spec 10.5). */
@Component
public class ComposerCutFileWriter implements CutFileWriter {

    private final CutComposer composer;
    private final FileStore files;

    public ComposerCutFileWriter(CutComposer composer, FileStore files) {
        this.composer = composer;
        this.files = files;
    }

    @Override
    public void write(Sheet sheet, Path sheetPdf, OutputStream out) throws IOException {
        composer.compose(sheet, id -> files.find(id).orElse(null), sheetPdf, out);
    }
}
