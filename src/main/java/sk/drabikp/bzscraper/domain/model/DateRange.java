package sk.drabikp.bzscraper.domain.model;

import java.time.LocalDate;

public record DateRange(LocalDate start, LocalDate end) {
    public DateRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end must not be null");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }

    public boolean containsInclusive(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
