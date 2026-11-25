package vn.minhtri.xuongin.shared;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings shared by several modules. Module-specific settings (TIFF, cut file...) live in
 * their own properties class inside the module so parallel work never edits this file.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Cors cors, Retention retention) {

    public record Storage(String path, long maxFileSize) {}

    public record Cors(List<String> origins) {}

    /** hours = 0 disables cleanup. */
    public record Retention(int hours, int sweepMinutes) {}
}
