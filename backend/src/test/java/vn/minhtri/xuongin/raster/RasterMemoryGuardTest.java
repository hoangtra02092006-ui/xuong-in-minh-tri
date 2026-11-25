package vn.minhtri.xuongin.raster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/** Spec 10.6: refuse before rendering, with a DPI to set. */
class RasterMemoryGuardTest {

    private static final long MB = 1024L * 1024L;

    @Test
    void estimateFor570By1000At300Dpi() {
        // 6732 x 11811 px: 3 bytes per sheet pixel + one band of (1024 + 64) rows x 13 bytes.
        long expected = 6732L * 11811 * 3 + 1088L * 6732 * 13;
        assertThat(RasterMemoryGuard.estimateBytes(6732, 11811, 1024)).isEqualTo(expected);
        assertThat(expected / MB).isEqualTo(318);
        // Short sheet: the band never exceeds the sheet.
        assertThat(RasterMemoryGuard.estimateBytes(100, 10, 1024)).isEqualTo(100L * 10 * 3 + 10L * 100 * 13);
    }

    @Test
    void fitsWithAOneGigabyteHeap() {
        RasterMemoryGuard guard = new RasterMemoryGuard(RasterProperties.defaults(), () -> 1024 * MB);
        assertThat(guard.allowedBytes()).isEqualTo(512 * MB);
        assertThat(guard.check(0, 570, 1000, 300)).isEqualTo(new RasterSize(6732, 11811));
    }

    @Test
    void smallHeapIsTiffTooLargeWithSuggestedDpi() {
        // 600 MB heap x 0.5 = 300 MB allowed. By hand: bytes(d) ~ 2650.6 d^2 + 317 400 d,
        // 290 DPI needs ~315.0 MB > 300 MB, 280 DPI ~296.7 MB fits.
        RasterMemoryGuard guard = new RasterMemoryGuard(RasterProperties.defaults(), () -> 600 * MB);
        ApiException ex = catchThrowableOfType(() -> guard.check(1, 570, 1000, 300), ApiException.class);
        assertThat(ex.code()).isEqualTo(ErrorCode.TIFF_TOO_LARGE);
        assertThat(ex.details()).containsEntry("suggestedDpi", 280).containsEntry("dpi", 300)
                .containsEntry("allowedMb", 300L).containsEntry("estimatedMb", 318L)
                .containsEntry("sheetIndex", 1);
        // At 300 DPI: (300 MB - 95.2 MB band) / (6732 x 3) = 10 861 rows = 919.6 mm -> 91 cm whole.
        assertThat(ex.details()).containsEntry("suggestedMaxLengthMm", 910.0);
        assertThat(ex.getMessage()).contains("Tấm 2 (57 × 100 cm)", "300 DPI", "Hãy đặt DPI khoảng 280",
                "giảm chiều dài tối đa mỗi file xuống 91 cm");
    }

    @Test
    void megapixelCapAppliesEvenWithAHugeHeap() {
        RasterMemoryGuard guard = new RasterMemoryGuard(RasterProperties.defaults(), () -> 64_000 * MB);
        // 570 x 2000 mm at 300 DPI = 6732 x 23622 = 159 MP > 100 MP. 230 DPI: 5161 x 18110 = 93 MP.
        ApiException ex = catchThrowableOfType(() -> guard.check(0, 570, 2000, 300), ApiException.class);
        assertThat(ex.code()).isEqualTo(ErrorCode.TIFF_TOO_LARGE);
        assertThat(ex.details()).containsEntry("suggestedDpi", 230).containsEntry("megapixels", 159L);
    }

    @Test
    void hopelessSheetSuggestsShorterFilesOnly() {
        RasterMemoryGuard guard = new RasterMemoryGuard(RasterProperties.defaults(), () -> MB);
        ApiException ex = catchThrowableOfType(() -> guard.check(0, 570, 1000, 300), ApiException.class);
        assertThat(ex.details()).doesNotContainKeys("suggestedDpi", "suggestedMaxLengthMm");
        assertThat(ex.getMessage()).contains("Hãy giảm chiều dài tối đa mỗi file.");
    }

    @Test
    void lengthOnlySuggestionWhenDpiIsAlreadyMinimal() {
        // 1 MP cap: 570 x 30 000 mm at 10 DPI = 224 x 11 811 px = 2.6 MP; 1 134 cm = 4 464.6 -> 4 464 rows x 224 = 999 936 px fits.
        RasterProperties props = new RasterProperties(null, 1, null, null, null);
        RasterMemoryGuard guard = new RasterMemoryGuard(props, () -> 64_000 * MB);
        ApiException ex = catchThrowableOfType(() -> guard.check(0, 570, 30_000, 10), ApiException.class);
        assertThat(ex.details()).doesNotContainKey("suggestedDpi").containsEntry("suggestedMaxLengthMm", 11_340.0);
        assertThat(ex.getMessage()).contains("Hãy giảm chiều dài tối đa mỗi file xuống");
    }

    @Test
    void springConstructorUsesTheRealHeap() {
        RasterMemoryGuard guard = new RasterMemoryGuard(RasterProperties.defaults());
        assertThat(guard.allowedBytes()).isEqualTo((long) (Runtime.getRuntime().maxMemory() * 0.5));
        assertThat(guard.estimateBytes(10, 10)).isEqualTo(RasterMemoryGuard.estimateBytes(10, 10, 1024));
    }
}
