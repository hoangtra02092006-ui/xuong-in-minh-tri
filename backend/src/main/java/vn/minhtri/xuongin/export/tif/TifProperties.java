package vn.minhtri.xuongin.export.tif;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the TIF encoder (spec 10.2 to 10.4, section 13). The raster module owns
 * {@code app.tiff} (DPI, band rows, heap fraction), so the encoder lives one level below.
 *
 * @param cmykProfile        ICC profile used for RGB to CMYK: a file path or {@code classpath:...}.
 *                           Adobe profiles are not in the repo (ADR-004), so production points
 *                           this at a file on the server
 * @param compression        compression of the composite image strips
 * @param compressionQuality 0..1, mapped to the Deflate level like ImageIO does (0.3 = level 3)
 * @param transparentLayer   add the Photoshop layer (tag 37724) so Photoshop shows a checkerboard
 * @param layerZip           compress layer channels with ZIP; false = RLE like the reference file
 * @param whiteChannelName   name of the spot channel, must match the RIP
 * @param whiteChokePixels   W1 is the ink mask contracted by this many pixels on every side
 * @param colorMode          CMYK (default, ICC profile) or RGB (sRGB, no W1), APP_TIFF_COLOR_MODE
 * @param whiteEnabled       add the W1 white-ink channel in CMYK mode, APP_TIFF_WHITE_ENABLED
 */
@ConfigurationProperties(prefix = "app.tiff.encoder")
public record TifProperties(
        @DefaultValue("classpath:color/USWebCoatedSWOP.icc") String cmykProfile,
        @DefaultValue("DEFLATE") TifCompression compression,
        @DefaultValue("0.3") double compressionQuality,
        @DefaultValue("true") boolean transparentLayer,
        @DefaultValue("true") boolean layerZip,
        @DefaultValue("W1") String whiteChannelName,
        @DefaultValue("1") int whiteChokePixels,
        @DefaultValue("CMYK") TifColorMode colorMode,
        @DefaultValue("true") boolean whiteEnabled) {

    @ConstructorBinding
    public TifProperties {
        if (compressionQuality < 0 || compressionQuality > 1) {
            throw new IllegalArgumentException("app.tiff.encoder.compression-quality must be in 0..1");
        }
        if (whiteChokePixels < 0) {
            throw new IllegalArgumentException("app.tiff.encoder.white-choke-pixels must be >= 0");
        }
        if (whiteChannelName == null || whiteChannelName.isBlank() || whiteChannelName.length() > 255) {
            throw new IllegalArgumentException("app.tiff.encoder.white-channel-name must be 1..255 characters");
        }
        colorMode = colorMode == null ? TifColorMode.CMYK : colorMode;
    }

    /** CMYK with W1, the print shop's default. */
    public TifProperties(String cmykProfile, TifCompression compression, double compressionQuality,
            boolean transparentLayer, boolean layerZip, String whiteChannelName, int whiteChokePixels) {
        this(cmykProfile, compression, compressionQuality, transparentLayer, layerZip, whiteChannelName,
                whiteChokePixels, TifColorMode.CMYK, true);
    }
}
