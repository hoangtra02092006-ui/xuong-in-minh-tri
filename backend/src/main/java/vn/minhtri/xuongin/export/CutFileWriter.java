package vn.minhtri.xuongin.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import vn.minhtri.xuongin.nesting.model.Sheet;

/**
 * Writes the Graphtec cut file of one sheet (spec 10.5). Implemented by the cut module; the
 * export endpoints answer INTERNAL_ERROR while no bean is present.
 */
public interface CutFileWriter {

    /**
     * @param sheetPdf the sheet's print PDF composed WITHOUT cut lines (invariant 10: the cut file
     *                 is rasterized from the same layout)
     * @param out      receives the cut PDF; not closed
     */
    void write(Sheet sheet, Path sheetPdf, OutputStream out) throws IOException;
}
