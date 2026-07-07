package sk.drabikp.bzscraper.adapter.out.persistence;

import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.EntryType;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.Location;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Translates between the {@link Gig} aggregate and its {@link GigEntity}. The only
 * class aware of both worlds. Schedule crosses as wall-clock {@code LocalDateTime}
 * plus country; the zone is rebuilt from the country on the way back.
 */
final class GigEntityMapper {

    private static final String LINEUP_DELIMITER = "\n";

    private GigEntityMapper() {
    }

    static String serializeId(GigId id) {
        return id.date() + "|" + id.venue();
    }

    static GigEntity toEntity(Gig gig) {
        ZonedDateTime end = gig.schedule().end();
        return new GigEntity(
                serializeId(gig.id()),
                gig.title(),
                gig.schedule().start().toLocalDateTime(),
                end != null ? end.toLocalDateTime() : null,
                gig.location().venue(),
                gig.location().city(),
                gig.location().country(),
                String.join(LINEUP_DELIMITER, gig.lineup()),
                gig.admission().type(),
                gig.admission().amount(),
                gig.description(),
                gig.facebookUrl(),
                gig.ticketUrl(),
                gig.posterImageUrl(),
                gig.cancelled());
    }

    static Gig toDomain(GigEntity e) {
        ZoneId zone = e.getCountry() != null ? ZoneId.of(e.getCountry().timezone()) : ZoneId.systemDefault();
        ZonedDateTime start = e.getStartDateTime().atZone(zone);
        ZonedDateTime end = e.getEndDateTime() != null ? e.getEndDateTime().atZone(zone) : null;

        return new Gig(
                e.getTitle(),
                new GigSchedule(start, end),
                new Location(e.getVenue(), e.getCity(), e.getCountry()),
                splitLineup(e.getLineup()),
                toAdmission(e.getEntryType(), e.getEntryFee()),
                e.getDescription(),
                e.getFacebookUrl(),
                e.getTicketUrl(),
                e.getPosterImageUrl(),
                e.isCancelled());
    }

    private static Admission toAdmission(EntryType type, String fee) {
        return switch (type) {
            case FREE -> Admission.free();
            case VOLUNTARY -> Admission.voluntary();
            case PAID -> Admission.paid(fee);
        };
    }

    private static List<String> splitLineup(String lineup) {
        if (lineup == null || lineup.isBlank()) {
            return List.of();
        }
        return Arrays.stream(lineup.split(LINEUP_DELIMITER)).filter(s -> !s.isBlank()).toList();
    }
}
