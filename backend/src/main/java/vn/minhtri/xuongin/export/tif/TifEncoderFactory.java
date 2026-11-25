package vn.minhtri.xuongin.export.tif;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/**
 * Opens {@link TifEncoder}s with the configured ICC profile and settings. The profile is loaded
 * once and cached; each encoder gets its own colour converter (ColorConvertOp is not shared
 * across threads).
 */
@Component
public class TifEncoderFactory {

    static final String CLASSPATH_PREFIX = "classpath:";
    private static final Logger log = LoggerFactory.getLogger(TifEncoderFactory.class);

    private final TifProperties properties;
    private volatile CmykProfile profile;

    /**
     * The profile and the exact bytes read from disk. {@link ICC_Profile#getData()} is not stable:
     * once the colour engine has used the profile it re-serialises it (557 KB became 702 KB in a
     * test), so the bytes embedded in tag 34675 are captured at load time.
     */
    record CmykProfile(ICC_Profile profile, byte[] bytes) {}

    public TifEncoderFactory(TifProperties properties) {
        this.properties = properties;
    }

    /**
     * Starts a TIF for one sheet.
     *
     * @param target  final file; written as {@code target.part} until {@link TifEncoder#finish()}
     * @param width   sheet width in pixels
     * @param height  sheet height in pixels
     * @param dpi     written to XResolution / YResolution
     * @param inkMask whole-sheet mask, row-major, non-zero = something prints (not contracted;
     *                the encoder contracts it for W1 by {@code whiteChokePixels})
     * @throws ApiException {@code INTERNAL_ERROR} when (CMYK mode) the ICC profile is missing or not CMYK
     */
    public TifEncoder open(Path target, int width, int height, int dpi, byte[] inkMask) throws IOException {
        TifEncoder.Options options;
        if (properties.colorMode() == TifColorMode.RGB) {
            // Spec 10.2 "rgb": RGB + sRGB, no W1. The CMYK profile is not needed.
            options = new TifEncoder.Options(null, srgbProfile(), properties.whiteChannelName(),
                    properties.whiteChokePixels(), properties.compression(), properties.compressionQuality(),
                    properties.transparentLayer(), properties.layerZip(), TifColorMode.RGB, false);
        } else {
            CmykProfile cmyk = cmykProfile();
            options = new TifEncoder.Options(new IccCmykConverter(cmyk.profile()), cmyk.bytes(),
                    properties.whiteChannelName(), properties.whiteChokePixels(), properties.compression(),
                    properties.compressionQuality(), properties.transparentLayer(), properties.layerZip(),
                    TifColorMode.CMYK, properties.whiteEnabled());
        }
        return new TifEncoder(target, width, height, dpi, inkMask, options);
    }

    /** Java's built-in sRGB profile, embedded in RGB TIFs (the pixels are rendered in sRGB). */
    static byte[] srgbProfile() {
        return ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
    }

    CmykProfile cmykProfile() {
        CmykProfile cached = profile;
        if (cached == null) {
            cached = loadProfile(properties.cmykProfile());
            profile = cached;
        }
        return cached;
    }

    static CmykProfile loadProfile(String location) {
        if (location == null || location.isBlank()) {
            throw misconfigured("Chưa khai báo hồ sơ màu CMYK. Đặt đường dẫn file .icc vào APP_TIFF_CMYK_PROFILE.");
        }
        byte[] bytes;
        ICC_Profile loaded;
        try (InputStream in = open(location)) {
            bytes = in.readAllBytes();
            loaded = ICC_Profile.getInstance(bytes);
        } catch (NoSuchFileException e) {
            throw misconfigured("Không tìm thấy hồ sơ màu CMYK \"" + location
                    + "\". Đặt đường dẫn file .icc vào APP_TIFF_CMYK_PROFILE (file này không có trong repo).");
        } catch (IOException | IllegalArgumentException e) {
            throw misconfigured("Không đọc được hồ sơ màu CMYK \"" + location + "\": " + e.getMessage());
        }
        if (loaded.getColorSpaceType() != ColorSpace.TYPE_CMYK) {
            throw misconfigured("File \"" + location + "\" không phải hồ sơ màu CMYK (có "
                    + loaded.getNumComponents() + " kênh màu).");
        }
        log.info("Loaded CMYK profile {} ({} bytes)", location, bytes.length);
        return new CmykProfile(loaded, bytes);
    }

    private static InputStream open(String location) throws IOException {
        if (location.startsWith(CLASSPATH_PREFIX)) {
            String name = location.substring(CLASSPATH_PREFIX.length());
            InputStream in = TifEncoderFactory.class.getClassLoader().getResourceAsStream(name);
            if (in == null) {
                throw new NoSuchFileException(location);
            }
            return in;
        }
        return Files.newInputStream(Path.of(location));
    }

    private static ApiException misconfigured(String message) {
        return new ApiException(ErrorCode.INTERNAL_ERROR, message);
    }
}
