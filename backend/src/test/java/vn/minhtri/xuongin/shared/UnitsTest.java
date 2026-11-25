package vn.minhtri.xuongin.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class UnitsTest {

    @Test
    void mmToCentiRoundsToNearestHundredth() {
        assertThat(Units.mmToCenti(570)).isEqualTo(57000);
        assertThat(Units.mmToCenti(33.866)).isEqualTo(3387);
        assertThat(Units.mmToCenti(0.004)).isZero();
    }

    @Test
    void centiRoundTrips() {
        assertThat(Units.centiToMm(Units.mmToCenti(50.8))).isEqualTo(50.8);
    }

    @Test
    void pointsMatchPdfDefinition() {
        assertThat(Units.mmToPt(25.4)).isCloseTo(72.0, within(1e-9));
        assertThat(Units.ptToMm(72.0)).isCloseTo(25.4, within(1e-9));
    }

    @Test
    void round2KeepsTwoDecimals() {
        assertThat(Units.round2(33.8666)).isEqualTo(33.87);
    }
}
