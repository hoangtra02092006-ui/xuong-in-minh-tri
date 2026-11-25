package vn.minhtri.xuongin.export.cut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import vn.minhtri.xuongin.export.cut.CutProperties.Corner;
import vn.minhtri.xuongin.export.cut.CutProperties.MarkShape;

class CutPropertiesTest {

    @Test
    void defaultsFollowSpecSection13() {
        CutProperties p = CutProperties.defaults();
        assertThat(p.dpi()).isEqualTo(300);
        assertThat(p.offsetMm()).isZero();
        assertThat(p.simplifyMm()).isEqualTo(0.1);
        assertThat(p.minAreaMm2()).isEqualTo(0.2);
        assertThat(p.strokePt()).isEqualTo(0.25);
        assertThat(p.spotName()).isEqualTo("CutContour");
        assertThat(p.markLengthMm()).isEqualTo(15.0);
        assertThat(p.markThicknessMm()).isEqualTo(1.0);
        assertThat(p.markClearanceMm()).isEqualTo(5.0);
        assertThat(p.cornerZoneMm()).isEqualTo(20.0);
        for (Corner corner : Corner.values()) {
            assertThat(p.shapeAt(corner)).isEqualTo(MarkShape.SQUARE_WITH_EDGE);
        }
        assertThat(p.artworkLayer()).isFalse();
    }

    @Test
    void bindsTheEnvironmentNamesOfTheSpec() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new SystemEnvironmentPropertySource("env", Map.of(
                "APP_CUT_DPI", "200",
                "APP_CUT_OFFSET_MM", "1.5",
                "APP_CUT_SPOT_NAME", "Thru-cut",
                "APP_CUT_MARK_LENGTH_MM", "12",
                "APP_CUT_MARK_TOP_LEFT", "L",
                "APP_CUT_MARK_BOTTOM_RIGHT", "SQUARE_FILLED",
                "APP_CUT_MARK_CLEARANCE_MM", "3")));
        CutProperties p = new Binder(ConfigurationPropertySources.from(sources))
                .bind("app.cut", CutProperties.class).get();
        assertThat(p.dpi()).isEqualTo(200);
        assertThat(p.offsetMm()).isEqualTo(1.5);
        assertThat(p.spotName()).isEqualTo("Thru-cut");
        assertThat(p.markLengthMm()).isEqualTo(12.0);
        assertThat(p.shapeAt(Corner.TOP_LEFT)).isEqualTo(MarkShape.L);
        assertThat(p.shapeAt(Corner.BOTTOM_RIGHT)).isEqualTo(MarkShape.SQUARE_FILLED);
        assertThat(p.shapeAt(Corner.TOP_RIGHT)).isEqualTo(MarkShape.SQUARE_WITH_EDGE);
        assertThat(p.cornerZoneMm()).isEqualTo(15.0);
    }

    @Test
    void rejectsNonsense() {
        assertThatThrownBy(() -> new CutProperties(0, null, null, null, null, null, null, null, null,
                null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CutProperties(null, -1.0, null, null, null, null, null, null, null,
                null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CutProperties(null, null, null, null, null, null, 10.0, 11.0, null,
                null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankSpotNameFallsBackToCutContour() {
        CutProperties p = new CutProperties(null, null, null, null, null, " ", null, null, null,
                null, null, null, null, null);
        assertThat(p.spotName()).isEqualTo("CutContour");
    }
}
