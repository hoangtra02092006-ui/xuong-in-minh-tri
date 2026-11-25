package vn.minhtri.xuongin.file;

import vn.minhtri.xuongin.shared.Units;

/** One item of the {@code POST /api/v1/files} response (spec 6.1). Sizes in mm, 2 decimals. */
public record FileResponse(
        String id,
        String originalName,
        double widthMm,
        double heightMm,
        double sourceWidthMm,
        double sourceHeightMm,
        boolean trimmed,
        FileType type,
        int pageCount,
        String previewUrl) {

    public static FileResponse from(StoredFile f) {
        return new FileResponse(
                f.id(),
                f.originalName(),
                Units.round2(f.widthMm()),
                Units.round2(f.heightMm()),
                Units.round2(f.sourceWidthMm()),
                Units.round2(f.sourceHeightMm()),
                f.trimmed(),
                f.type(),
                f.pageCount(),
                FileController.BASE_PATH + "/" + f.id() + "/preview");
    }
}
