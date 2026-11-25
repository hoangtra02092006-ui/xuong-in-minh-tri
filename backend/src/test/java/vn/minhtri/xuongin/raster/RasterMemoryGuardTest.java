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
        // Workers cannot change the server DPI: the length comes first, the DPI goes to the manager.
        assertThat(ex.getMessage()).contains("Tấm 2 (57 × 100 cm)", "300 DPI",
                "Hãy giảm chiều dài tối đa mỗi file xuống 91 cm", "báo người quản lý đặt DPI của máy chủ khoảng 280");
        assertThat(ex.getMessage().indexOf("giảm chiều dài")).isLessThan(ex.getMessage().indexOf("DPI của máy chủ"));
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
        assertThat(ex.getMessage()).contains("Hãy giảm chiều dài tối đa mỗi file rồi ghép lại.")
                .doesNotContain("người quản lý");
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

    // ---- shared budget across concurrent builds ----

    private static RasterMemoryGuard budget(long allowedMb) {
        return new RasterMemoryGuard(RasterProperties.defaults(), () -> 2 * allowedMb * MB);
    }

    private static ApiException busy() {
        return new ApiException(ErrorCode.JOB_NOT_READY, "bận");
    }

    @Test
    void reservationsShareTheBudgetAndGiveItBack() {
        RasterMemoryGuard guard = budget(100);
        try (RasterMemoryGuard.Reservation a = guard.reserve(60 * MB, 1_000, RasterMemoryGuardTest::busy);
                RasterMemoryGuard.Reservation b = guard.reserve(40 * MB, 1_000, RasterMemoryGuardTest::busy)) {
            assertThat(guard.reservedBytes()).isEqualTo(100 * MB);
            a.close();
            a.close(); // closing twice gives back once
            assertThat(guard.reservedBytes()).isEqualTo(40 * MB);
            assertThat(b.bytes()).isEqualTo(40 * MB);
        }
        assertThat(guard.reservedBytes()).isZero();
    }

    @Test
    void fullBudgetMakesTheNextBuildWaitThenGiveUp() {
        RasterMemoryGuard guard = budget(100);
        try (RasterMemoryGuard.Reservation tif = guard.reserve(80 * MB, 1_000, RasterMemoryGuardTest::busy)) {
            long started = System.nanoTime();
            ApiException ex = catchThrowableOfType(
                    () -> guard.reserve(30 * MB, 100, RasterMemoryGuardTest::busy), ApiException.class);
            assertThat(ex.code()).isEqualTo(ErrorCode.JOB_NOT_READY);
            assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(90);
            assertThat(guard.reservedBytes()).isEqualTo(80 * MB);
        }
    }

    @Test
    void oversizedReservationIsCutToTheBudgetAndRunsAlone() throws Exception {
        RasterMemoryGuard guard = budget(100);
        try (RasterMemoryGuard.Reservation photo = guard.reserve(700 * MB, 1_000, RasterMemoryGuardTest::busy)) {
            assertThat(photo.bytes()).isEqualTo(100 * MB);
            ApiException ex = catchThrowableOfType(
                    () -> guard.reserve(1, 50, RasterMemoryGuardTest::busy), ApiException.class);
            assertThat(ex.code()).isEqualTo(ErrorCode.JOB_NOT_READY);
        }
    }

    @Test
    void waiterProceedsWhenRoomIsFreedFirstComeFirstServed() throws Exception {
        RasterMemoryGuard guard = budget(100);
        java.util.List<String> order = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        RasterMemoryGuard.Reservation running = guard.reserve(90 * MB, 1_000, RasterMemoryGuardTest::busy);
        Thread big = new Thread(() -> {
            try (RasterMemoryGuard.Reservation r = guard.reserve(80 * MB, 5_000, RasterMemoryGuardTest::busy)) {
                order.add("big");
            }
        });
        big.start();
        awaitWaiting(big);
        // Would fit next to the running build, but the big one asked first.
        Thread small = new Thread(() -> {
            try (RasterMemoryGuard.Reservation r = guard.reserve(10 * MB, 5_000, RasterMemoryGuardTest::busy)) {
                order.add("small");
            }
        });
        small.start();
        awaitWaiting(small);
        assertThat(order).isEmpty();
        running.close();
        big.join(5_000);
        small.join(5_000);
        assertThat(order).containsExactly("big", "small");
        assertThat(guard.reservedBytes()).isZero();
    }

    @Test
    void interruptedWaitGivesUpAndKeepsTheFlag() throws Exception {
        RasterMemoryGuard guard = budget(100);
        java.util.concurrent.atomic.AtomicReference<Object> outcome = new java.util.concurrent.atomic.AtomicReference<>();
        try (RasterMemoryGuard.Reservation running = guard.reserve(100 * MB, 1_000, RasterMemoryGuardTest::busy)) {
            Thread waiter = new Thread(() -> {
                try {
                    guard.reserve(MB, 10_000, RasterMemoryGuardTest::busy);
                } catch (ApiException e) {
                    outcome.set(Thread.currentThread().isInterrupted() ? e.code() : "flag lost");
                }
            });
            waiter.start();
            awaitWaiting(waiter);
            waiter.interrupt();
            waiter.join(5_000);
        }
        assertThat(outcome.get()).isEqualTo(ErrorCode.JOB_NOT_READY);
        // The abandoned ticket does not block the queue.
        try (RasterMemoryGuard.Reservation next = guard.reserve(MB, 100, RasterMemoryGuardTest::busy)) {
            assertThat(next.bytes()).isEqualTo(MB);
        }
    }

    @Test
    void outOfMemoryIsTiffTooLargeWithGuidance() {
        ApiException ex = RasterMemoryGuard.outOfMemory(2, "cắt");
        assertThat(ex.code()).isEqualTo(ErrorCode.TIFF_TOO_LARGE);
        assertThat(ex.getMessage()).contains("file cắt tấm 3", "giảm chiều dài tối đa mỗi file", "báo người quản lý");
        assertThat(ex.details()).containsEntry("sheetIndex", 2);
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (thread.getState() != Thread.State.TIMED_WAITING && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(thread.getState()).isEqualTo(Thread.State.TIMED_WAITING);
    }
}
