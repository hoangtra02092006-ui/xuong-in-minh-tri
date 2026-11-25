package vn.minhtri.xuongin.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OccupancyMaskTest {

    @Test
    void setAndReadCellsOutOfRangeIsOccupied() {
        var m = OccupancyMask.empty(70, 3, 0.5);
        m.set(65, 2);
        assertThat(m.isOccupied(65, 2)).isTrue();
        assertThat(m.isOccupied(64, 2)).isFalse();
        assertThat(m.isOccupied(-1, 0)).isTrue();
        assertThat(m.isOccupied(70, 0)).isTrue();
    }

    @Test
    void trimmedComparesWithSourceSize() {
        var f = new StoredFile("a", "a.pdf", "/x", FileType.PDF, 30, 30, 210, 297, null, null, 0, 1, true, 1, null);
        assertThat(f.trimmed()).isTrue();
    }
}
