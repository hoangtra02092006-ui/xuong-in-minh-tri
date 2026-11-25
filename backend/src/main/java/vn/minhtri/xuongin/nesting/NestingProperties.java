package vn.minhtri.xuongin.nesting;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Safety limits of a nesting request ({@code app.nesting.*}). A request with billions of pieces
 * or pieces of 0.01 mm would run the nesting thread out of memory on the shared server.
 *
 * @param maxTotalPieces most pieces (sum of quantities) one job may place (APP_NESTING_MAX_PIECES)
 * @param minPieceMm     shortest allowed piece side in mm, after overrides (APP_NESTING_MIN_PIECE_MM)
 */
@ConfigurationProperties(prefix = "app.nesting")
public record NestingProperties(
        @DefaultValue("10000") int maxTotalPieces,
        @DefaultValue("1.0") double minPieceMm) {

    public NestingProperties {
        if (maxTotalPieces <= 0) {
            throw new IllegalArgumentException("app.nesting.max-total-pieces must be > 0");
        }
        if (minPieceMm <= 0) {
            throw new IllegalArgumentException("app.nesting.min-piece-mm must be > 0");
        }
    }

    public static NestingProperties defaults() {
        return new NestingProperties(10_000, 1.0);
    }
}
