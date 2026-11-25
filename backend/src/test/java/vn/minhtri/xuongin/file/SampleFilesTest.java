package vn.minhtri.xuongin.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every file in samples/ reads to the size in its name or README (spec 15 "Đọc file"), within 0.1 mm. */
class SampleFilesTest {

    private static final double TOLERANCE_MM = 0.1;

    private final FileProperties props = new FileProperties();
    private final PdfMetadataReader pdfReader = new PdfMetadataReader(props);
    private final ImageMetadataReader imageReader = new ImageMetadataReader(props);

    static Stream<Arguments> fullPageLabels() {
        return Stream.of(
                Arguments.of("nhan-7x3.pdf", 70.0, 30.0),
                Arguments.of("nhan-5x7.pdf", 50.0, 70.0),
                Arguments.of("nhan-13x4.pdf", 130.0, 40.0),
                Arguments.of("nhan-4x17.pdf", 40.0, 170.0),
                Arguments.of("nhan-16x13.pdf", 160.0, 130.0),
                Arguments.of("nhan-20x18.pdf", 200.0, 180.0),
                Arguments.of("logo-30x5.pdf", 300.0, 50.0),
                Arguments.of("loi-qua-kho-60x59.pdf", 600.0, 590.0));
    }

    @ParameterizedTest(name = "{0} -> {1} x {2} mm")
    @MethodSource("fullPageLabels")
    void labelPdfReadsToTheSizeInItsName(String name, double wMm, double hMm) throws IOException {
        SourceMetadata m = pdfReader.read(TestFiles.sample(name));

        assertThat(m.type()).isEqualTo(FileType.PDF);
        assertThat(m.widthMm()).isCloseTo(wMm, within(TOLERANCE_MM));
        assertThat(m.heightMm()).isCloseTo(hMm, within(TOLERANCE_MM));
        // Artwork fills the page (solid background), so nothing is trimmed.
        assertThat(m.sourceWidthMm()).isCloseTo(wMm, within(TOLERANCE_MM));
        assertThat(m.sourceHeightMm()).isCloseTo(hMm, within(TOLERANCE_MM));
        assertThat(m.pageCount()).isEqualTo(1);
        assertThat(m.pageRotation()).isZero();
        assertThat(m.hasAlpha()).isTrue();
        assertThat(m.occupancy()).isNotNull();
        assertThat(m.occupancy().cols()).isEqualTo((int) Math.round(wMm / OccupancyMask.DEFAULT_CELL_MM));
    }

    @Test
    void smallFigureOnA4IsTrimmedToItsContentBox() throws IOException {
        SourceMetadata m = pdfReader.read(TestFiles.sample("thu-hinh-nho-giua-A4.pdf"));

        // Figure "255.12 396.85 85.04 85.04 re f" = 30 x 30 mm on a 210 x 297 mm page.
        assertThat(m.widthMm()).isCloseTo(30.0, within(TOLERANCE_MM));
        assertThat(m.heightMm()).isCloseTo(30.0, within(TOLERANCE_MM));
        assertThat(m.sourceWidthMm()).isCloseTo(210.0, within(TOLERANCE_MM));
        assertThat(m.sourceHeightMm()).isCloseTo(297.0, within(TOLERANCE_MM));
        assertThat(m.contentBox().xPt()).isCloseTo(255.12, within(0.01));
        assertThat(m.contentBox().yPt()).isCloseTo(396.85, within(0.01));
        assertThat(m.contentBox().widthPt()).isCloseTo(85.04, within(0.01));
        assertThat(toStored(m).trimmed()).isTrue();
    }

    @Test
    void severalFiguresOnA3KeepOneUnionBox() throws IOException {
        SourceMetadata m = pdfReader.read(TestFiles.sample("thu-nhieu-hinh-trong-A3.pdf"));

        // Union of the three rectangles: x 226.77..566.93 pt, y 425.20..737.01 pt = 120 x 110 mm.
        assertThat(m.widthMm()).isCloseTo(120.0, within(TOLERANCE_MM));
        assertThat(m.heightMm()).isCloseTo(110.0, within(TOLERANCE_MM));
        assertThat(m.sourceWidthMm()).isCloseTo(297.0, within(TOLERANCE_MM));
        assertThat(m.sourceHeightMm()).isCloseTo(420.0, within(TOLERANCE_MM));
        assertThat(m.contentBox().xPt()).isCloseTo(226.77, within(0.01));
        assertThat(m.contentBox().yPt()).isCloseTo(425.20, within(0.01));
    }

    @Test
    void lShapeIs108mmSquareWithAnEmptyTopRightInTheMask() throws IOException {
        SourceMetadata m = pdfReader.read(TestFiles.sample("thu-chu-L-108.pdf"));

        assertThat(m.widthMm()).isCloseTo(108.0, within(TOLERANCE_MM));
        assertThat(m.heightMm()).isCloseTo(108.0, within(TOLERANCE_MM));

        // Bottom bar 108 x 58 mm, upright 58 x 50 mm on its left: top-right 50 x 50 mm is empty.
        OccupancyMask mask = m.occupancy();
        assertThat(mask.cols()).isEqualTo(216);
        assertThat(mask.rows()).isEqualTo(216);
        assertThat(mask.isOccupied(cell(5), cell(5))).isTrue();
        assertThat(mask.isOccupied(cell(100), cell(5))).isTrue();
        assertThat(mask.isOccupied(cell(5), cell(100))).isTrue();
        assertThat(mask.isOccupied(cell(80), cell(80))).isFalse();
        assertThat(mask.isOccupied(cell(107), cell(107))).isFalse();
        // Edges round outward by at most one cell.
        assertThat(mask.isOccupied(cell(57.7), cell(80))).isTrue();
        assertThat(mask.isOccupied(cell(59), cell(80))).isFalse();
        assertThat(mask.isOccupied(cell(80), cell(59))).isFalse();
        assertThat(emptyCells(mask)).isBetween(98 * 98, 100 * 100);
    }

    @Test
    void png300DpiIs50_8By33_87mm() throws IOException {
        SourceMetadata m = imageReader.read(TestFiles.sample("anh-600x400-300dpi.png"));

        assertThat(m.type()).isEqualTo(FileType.IMAGE);
        assertThat(m.widthMm()).isCloseTo(50.8, within(0.01));
        assertThat(m.heightMm()).isCloseTo(33.87, within(0.01));
        assertThat(m.sourceWidthMm()).isEqualTo(m.widthMm());
        assertThat(m.hasAlpha()).isFalse();
        assertThat(m.occupancy()).isNull();
        assertThat(m.pageCount()).isEqualTo(1);
        assertThat(toStored(m).trimmed()).isFalse();
    }

    @Test
    void jpgWithoutDpiUses72Dpi() throws IOException {
        // 4200 x 3000 px, JFIF density 1x1 "aspect ratio only" = no DPI -> 72 DPI.
        SourceMetadata m = imageReader.read(TestFiles.sample("thu-anh-lon-3mb.jpg"));

        assertThat(m.widthMm()).isCloseTo(4200 * 25.4 / 72, within(0.01));
        assertThat(m.heightMm()).isCloseTo(3000 * 25.4 / 72, within(0.01));
        assertThat(m.hasAlpha()).isFalse();
    }

    @Test
    void everySampleIsCovered() throws IOException {
        try (Stream<java.nio.file.Path> files = Files.list(TestFiles.samples())) {
            assertThat(files.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches(".*\\.(pdf|png|jpe?g)"))
                    .filter(n -> !n.startsWith("IN6")))
                    .hasSize(13);
        }
    }

    private static int cell(double mm) {
        return (int) (mm / OccupancyMask.DEFAULT_CELL_MM);
    }

    private static int emptyCells(OccupancyMask mask) {
        int empty = 0;
        for (int r = 0; r < mask.rows(); r++) {
            for (int c = 0; c < mask.cols(); c++) {
                if (!mask.isOccupied(c, r)) {
                    empty++;
                }
            }
        }
        return empty;
    }

    private static StoredFile toStored(SourceMetadata m) {
        return new StoredFile("id", "n", "p", m.type(), m.widthMm(), m.heightMm(), m.sourceWidthMm(), m.sourceHeightMm(),
                m.contentBox(), m.occupancy(), m.pageRotation(), m.pageCount(), m.hasAlpha(), 0, java.time.Instant.EPOCH);
    }
}
