package vn.minhtri.xuongin.export;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the export module ({@code app.export.*}, spec 13).
 *
 * @param tifDpi        DPI of the TIF (APP_TIFF_DPI)
 * @param waitTimeoutMs how long a download waits for a TIF that is still being built
 *                      (APP_EXPORT_TIMEOUT_MS, same bound as the async response timeout)
 */
@ConfigurationProperties(prefix = "app.export")
public record ExportProperties(
        @DefaultValue("300") int tifDpi,
        @DefaultValue("1800000") long waitTimeoutMs) {

    public ExportProperties {
        if (tifDpi <= 0) {
            throw new IllegalArgumentException("app.export.tif-dpi must be > 0");
        }
        if (waitTimeoutMs <= 0) {
            throw new IllegalArgumentException("app.export.wait-timeout-ms must be > 0");
        }
    }
}
