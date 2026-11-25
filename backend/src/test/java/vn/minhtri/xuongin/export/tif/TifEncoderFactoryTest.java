package vn.minhtri.xuongin.export.tif;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.minhtri.xuongin.export.tif.TiffTestReader.Tiff;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/**
 * Factory, settings and real ICC conversion. The Adobe profile is not in the repo (ADR-004):
 * tests that need it look at {@code -Dapp.tiff.cmyk-profile}, then {@code APP_TIFF_CMYK_PROFILE},
 * then the old app's copy on the manager's machine, and are skipped when none exists (CI).
 */
class TifEncoderFactoryTest {

    private static final String REFERENCE_PROFILE =
            "/home/user/hoangtra02092006-ui/push-clean-leather-file/backend/src/main/resources/color/USWebCoatedSWOP.icc";

    @TempDir
    Path dir;

    static Path swopProfile() {
        String configured = System.getProperty("app.tiff.cmyk-profile", System.getenv("APP_TIFF_CMYK_PROFILE"));
        Path path = Path.of(configured != null && !configured.startsWith(TifEncoderFactory.CLASSPATH_PREFIX)
                ? configured : REFERENCE_PROFILE);
        assumeTrue(Files.exists(path), "CMYK ICC profile not available: " + path);
        return path;
    }

    static TifProperties props(String profile) {
        return new TifProperties(profile, TifCompression.DEFLATE, 0.3, true, true, "W1", 1);
    }

    @Test
    void bindsDefaultsFromSpecSection13() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .run(context -> {
                    TifProperties p = context.getBean(TifProperties.class);
                    assertThat(p.cmykProfile()).isEqualTo("classpath:color/USWebCoatedSWOP.icc");
                    assertThat(p.compression()).isEqualTo(TifCompression.DEFLATE);
                    assertThat(p.compressionQuality()).isEqualTo(0.3);
                    assertThat(p.transparentLayer()).isTrue();
                    assertThat(p.layerZip()).isTrue();
                    assertThat(p.whiteChannelName()).isEqualTo("W1");
                    assertThat(p.whiteChokePixels()).isEqualTo(1);
                });
    }

    @Test
    void bindsOverrides() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.tiff.encoder.compression=LZW", "app.tiff.encoder.layer-zip=false",
                        "app.tiff.encoder.cmyk-profile=/srv/icc/printer.icc", "app.tiff.encoder.white-choke-pixels=2")
                .run(context -> {
                    TifProperties p = context.getBean(TifProperties.class);
                    assertThat(p.compression()).isEqualTo(TifCompression.LZW);
                    assertThat(p.layerZip()).isFalse();
                    assertThat(p.cmykProfile()).isEqualTo("/srv/icc/printer.icc");
                    assertThat(p.whiteChokePixels()).isEqualTo(2);
                });
    }

    @EnableConfigurationProperties(TifProperties.class)
    static class Config {}

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new TifProperties("x", TifCompression.LZW, 1.5, true, true, "W1", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TifProperties("x", TifCompression.LZW, 0.3, true, true, "W1", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TifProperties("x", TifCompression.LZW, 0.3, true, true, " ", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingProfileFailsWithAClearError() {
        TifEncoderFactory factory = new TifEncoderFactory(props(dir.resolve("none.icc").toString()));
        assertThatThrownBy(() -> factory.open(dir.resolve("a.tif"), 1, 1, 300, new byte[1]))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Không tìm thấy hồ sơ màu CMYK")
                .hasMessageContaining("APP_TIFF_CMYK_PROFILE")
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        assertThat(dir.resolve("a.tif.part")).doesNotExist();
    }

    @Test
    void missingClasspathProfileAndBlankSettingFail() {
        assertThatThrownBy(() -> TifEncoderFactory.loadProfile("classpath:color/nope.icc"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Không tìm thấy");
        assertThatThrownBy(() -> TifEncoderFactory.loadProfile(" "))
                .isInstanceOf(ApiException.class).hasMessageContaining("Chưa khai báo");
    }

    @Test
    void rejectsAFileThatIsNotAnIccProfile() throws IOException {
        Path junk = Files.writeString(dir.resolve("junk.icc"), "not a profile");
        assertThatThrownBy(() -> TifEncoderFactory.loadProfile(junk.toString()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Không đọc được");
    }

    @Test
    void rejectsAnRgbProfile() throws IOException {
        Path srgb = Files.write(dir.resolve("srgb.icc"), ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData());
        assertThatThrownBy(() -> TifEncoderFactory.loadProfile(srgb.toString()))
                .isInstanceOf(ApiException.class).hasMessageContaining("không phải hồ sơ màu CMYK");
    }

    @Test
    void realIccProfileProducesCmykAndEmbedsTheProfile() throws IOException {
        Path profile = swopProfile();
        TifEncoderFactory factory = new TifEncoderFactory(props(profile.toString()));
        assertThat(factory.cmykProfile()).isSameAs(factory.cmykProfile());

        int[] argb = TifEncoderTest.sheet();
        byte[] mask = TifEncoderTest.maskOf(argb);
        byte[] whole = encodeWith(factory, "whole.tif", argb, mask, TifEncoderTest.H);
        byte[] banded = encodeWith(factory, "banded.tif", argb, mask, 7);
        assertThat(Arrays.equals(banded, whole)).as("bands of 7 rows give the same bytes as the whole sheet").isTrue();

        Tiff tif = TiffTestReader.parse(whole);
        assertThat(tif.raw(TifEncoder.TAG_ICC_PROFILE)).isEqualTo(Files.readAllBytes(profile));
        byte[] samples = tif.samples();

        // Transparent background flattened onto white: no ink at all (not black, not grey).
        assertThat(cmyk(samples, 0, 0)).containsExactly(0, 0, 0, 0);
        // Opaque white artwork: also no colour ink, but W1 is set.
        assertThat(cmyk(samples, 120, 40)).containsExactly(0, 0, 0, 0);
        assertThat(samples[(40 * TifEncoderTest.W + 120) * 5 + 4]).isEqualTo((byte) 0);
        // Pure red through SWOP: strong magenta and yellow, almost no cyan.
        int[] red = cmyk(samples, 30, 20);
        assertThat(red[1]).isGreaterThan(200);
        assertThat(red[2]).isGreaterThan(200);
        assertThat(red[0]).isLessThan(30);
    }

    private byte[] encodeWith(TifEncoderFactory factory, String name, int[] argb, byte[] mask, int bandRows)
            throws IOException {
        int w = TifEncoderTest.W;
        int h = TifEncoderTest.H;
        Path target = dir.resolve(name);
        try (TifEncoder encoder = factory.open(target, w, h, 300, mask)) {
            for (int y = 0; y < h; y += bandRows) {
                int rows = Math.min(bandRows, h - y);
                int[] band = new int[rows * w];
                System.arraycopy(argb, y * w, band, 0, band.length);
                encoder.accept(y, rows, w, band);
            }
            encoder.finish();
        }
        return Files.readAllBytes(target);
    }

    private static int[] cmyk(byte[] samples, int x, int y) {
        int i = (y * TifEncoderTest.W + x) * 5;
        return new int[] {samples[i] & 0xFF, samples[i + 1] & 0xFF, samples[i + 2] & 0xFF, samples[i + 3] & 0xFF};
    }
}
