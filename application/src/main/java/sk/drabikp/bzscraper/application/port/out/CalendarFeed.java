package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.CalendarEvent;

import java.util.List;

/** The band's calendar, read-only. */
public interface CalendarFeed {

    /** False when no calendar address is configured. */
    boolean configured();

    /** Every event in the calendar, past and future. */
    List<CalendarEvent> read() throws CalendarUnavailableException;
}
