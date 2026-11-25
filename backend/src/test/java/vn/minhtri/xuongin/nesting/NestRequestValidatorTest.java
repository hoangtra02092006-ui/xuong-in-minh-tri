package vn.minhtri.xuongin.nesting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.file.ContentBox;
import vn.minhtri.xuongin.file.FileType;
import vn.minhtri.xuongin.file.OccupancyMask;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.nesting.model.NestItemRequest;
import vn.minhtri.xuongin.nesting.model.NestRequest;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/** Spec 6.3 synchronous checks and spec 8.1 / 8.4 size and shape resolution. */
class NestRequestValidatorTest {

    static final OccupancyMask MASK = OccupancyMask.empty(4, 4, 0.5);

    static StoredFile file(String id, FileType type, double w, double h, OccupancyMask mask, int rotation) {
        return new StoredFile(id, id + ".pdf", id + ".pdf", type, w, h, w, h, new ContentBox(0, 0, w, h), mask,
                rotation, 1, true, 10, Instant.EPOCH);
    }

    static NestRequest request(double width, double margin, boolean rotateGlobal, NestItemRequest... items) {
        return new NestRequest(null, width, margin, 3, null, rotateGlobal, false, List.of(items));
    }

    static NestItemRequest item(String id, boolean rotate, Double w, Double h) {
        return new NestItemRequest(id, 2, rotate, w, h);
    }

    @Test
    void unknownFileIsFileNotFound() {
        NestRequest req = request(570, 5, true, item("a", true, null, null), item("x", true, null, null));
        Map<String, StoredFile> known = Map.of("a", file("a", FileType.PDF, 10, 10, null, 0));
        assertThatThrownBy(() -> NestRequestValidator.requireFiles(req, known::get))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.FILE_NOT_FOUND);
                    assertThat(e.details()).containsEntry("fileId", "x");
                });
    }

    @Test
    void sizeOverrideCategoryAndRotation() {
        StoredFile a = file("a", FileType.PDF, 70, 30, MASK, 0);
        StoredFile b = file("b", FileType.IMAGE, 50, 70, null, 0);
        NestRequest req = request(570, 5, true, item("a", true, null, null), item("b", false, 40.0, 60.0),
                item("a", true, 70.0, 30.0));
        List<ResolvedItem> items = NestRequestValidator.resolve(req, Map.of("a", a, "b", b));
        assertThat(items).extracting(ResolvedItem::categoryIndex).containsExactly(0, 1, 0);
        assertThat(items.get(1).widthMm()).isEqualTo(40.0);
        assertThat(items.get(1).heightMm()).isEqualTo(60.0);
        assertThat(items.get(0).widthMm()).isEqualTo(70.0);
        assertThat(items).extracting(ResolvedItem::allowRotate).containsExactly(true, false, true);
        assertThat(items.get(0).label()).isEqualTo("a.pdf");

        List<ResolvedItem> noRotate = NestRequestValidator.resolve(request(570, 5, false, item("a", true, null, null)),
                Map.of("a", a));
        assertThat(noRotate.get(0).allowRotate()).isFalse();
    }

    @Test
    void shapeOnlyForUneditedUnrotatedVectorPdf() {
        assertThat(NestRequestValidator.shapeOf(file("a", FileType.PDF, 70, 30, MASK, 0), 70.005, 30)).isSameAs(MASK);
        assertThat(NestRequestValidator.shapeOf(file("a", FileType.PDF, 70, 30, MASK, 0), 70.5, 30)).isNull();
        assertThat(NestRequestValidator.shapeOf(file("a", FileType.PDF, 70, 30, MASK, 90), 70, 30)).isNull();
        assertThat(NestRequestValidator.shapeOf(file("a", FileType.PDF, 70, 30, null, 0), 70, 30)).isNull();
        assertThat(NestRequestValidator.shapeOf(file("a", FileType.IMAGE, 70, 30, MASK, 0), 70, 30)).isNull();
    }

    @Test
    void pieceWiderThanUsableWidthEvenRotated() {
        StoredFile wide = file("w", FileType.PDF, 600, 590, null, 0);
        NestRequest req = request(570, 5, true, item("w", true, null, null));
        List<ResolvedItem> items = NestRequestValidator.resolve(req, Map.of("w", wide));
        assertThatThrownBy(() -> NestRequestValidator.checkWidths(req, items))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.ITEM_WIDER_THAN_SHEET);
                    assertThat(e.getMessage())
                            .isEqualTo("Hình \"w.pdf\" 60x59 cm rộng hơn khổ 57 cm (đã tính cả trường hợp xoay).");
                    assertThat(e.details()).containsEntry("fileId", "w").containsEntry("label", "w.pdf");
                });
    }

    @Test
    void rotationRescuesALongPieceOnlyWhenAllowed() {
        StoredFile banner = file("b", FileType.PDF, 600, 200, null, 0);
        NestRequest rotate = request(570, 5, true, item("b", true, null, null));
        NestRequestValidator.checkWidths(rotate, NestRequestValidator.resolve(rotate, Map.of("b", banner)));

        NestRequest fixed = request(570, 5, true, item("b", false, null, null));
        assertThatThrownBy(() -> NestRequestValidator.checkWidths(fixed,
                        NestRequestValidator.resolve(fixed, Map.of("b", banner))))
                .hasMessage("Hình \"b.pdf\" 60x20 cm rộng hơn khổ 57 cm và không được phép xoay.");
    }

    @Test
    void exactFitIsAcceptedAndHugeMarginIsLeftToTheEngine() {
        StoredFile exact = file("e", FileType.PDF, 560, 100, null, 0);
        NestRequest req = request(570, 5, false, item("e", false, null, null));
        NestRequestValidator.checkWidths(req, NestRequestValidator.resolve(req, Map.of("e", exact)));

        NestRequest noRoom = request(100, 50, false, item("e", false, null, null));
        NestRequestValidator.checkWidths(noRoom, NestRequestValidator.resolve(noRoom, Map.of("e", exact)));
    }

    @Test
    void centimetresInVietnameseStyle() {
        assertThat(NestRequestValidator.cm(570)).isEqualTo("57");
        assertThat(NestRequestValidator.cm(25)).isEqualTo("2,5");
        assertThat(NestRequestValidator.cm(33.87)).isEqualTo("3,39");
    }
}
