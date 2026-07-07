package sk.drabikp.bzscraper.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GigScheduleTest {

    private static ZonedDateTime at(int y, int m, int d, int h) {
        return ZonedDateTime.of(y, m, d, h, 0, 0, 0, ZoneId.of("Europe/Prague"));
    }

    @Test
    void a_start_only_schedule_is_valid() {
        GigSchedule s = GigSchedule.startingAt(at(2026, 9, 15, 20));
        assertThat(s.hasEnd()).isFalse();
        assertThat(s.startDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void start_is_required() {
        assertThatThrownBy(() -> new GigSchedule(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void end_must_not_be_before_start() {
        assertThatThrownBy(() -> new GigSchedule(at(2026, 9, 15, 20), at(2026, 9, 15, 19)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void end_equal_to_or_after_start_is_valid() {
        assertThat(new GigSchedule(at(2026, 9, 15, 20), at(2026, 9, 15, 23)).hasEnd()).isTrue();
    }
}
