package sk.drabikp.bzscraper.calendar.adapter.in.rest;

import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarChangeJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarCountsJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarDraftJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarFilterJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarKindJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarOverviewJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CalendarRowJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.CatalogMatchJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.GigRefJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.MatchDifferenceJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.ReasonJson;
import sk.drabikp.bzscraper.calendar.adapter.in.rest.api.RuleJson;
import sk.drabikp.bzscraper.calendar.domain.CalendarChange;
import sk.drabikp.bzscraper.calendar.domain.CalendarFilter;
import sk.drabikp.bzscraper.calendar.domain.CalendarGigDraft;
import sk.drabikp.bzscraper.calendar.domain.CalendarOverview;
import sk.drabikp.bzscraper.calendar.domain.CalendarRow;
import sk.drabikp.bzscraper.calendar.domain.CatalogMatch;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEvent;
import sk.drabikp.bzscraper.calendar.domain.event.CalendarEventKind;
import sk.drabikp.bzscraper.calendar.domain.rules.CalendarClassification;
import sk.drabikp.bzscraper.calendar.domain.rules.ProfileRule;
import sk.drabikp.bzscraper.gig.api.GigDraftMapping;
import sk.drabikp.bzscraper.gig.domain.Gig;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The band calendar as the API sends it (calendar.yaml): rows latest first, with the views each is in. */
final class CalendarMapping {

    private CalendarMapping() {
    }

    static CalendarOverviewJson unconfigured() {
        return new CalendarOverviewJson(false, null, null, List.of());
    }

    static CalendarOverviewJson toJson(CalendarOverview overview, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        List<CalendarRow> rows = overview.rows().stream()
                .sorted(Comparator.comparing((CalendarRow r) -> r.event().start()).reversed()).toList();
        Set<String> linkable = CalendarFilter.linkable(rows).stream().map(CalendarRow::eventId)
                .collect(Collectors.toSet());
        CalendarFilter.Counts c = CalendarFilter.count(rows, today);
        return new CalendarOverviewJson(true, overview.lastRead(),
                new CalendarCountsJson(c.events(), c.gigs(), c.notSure(), c.missing(), c.changes(), c.differences(),
                        c.linkable(), c.decided()),
                rows.stream().map(r -> row(r, today, linkable.contains(r.eventId()))).toList());
    }

    static List<RuleJson> toJson(List<ProfileRule> rules) {
        return rules.stream().map(r -> new RuleJson(r.kind().name(), r.value(), r.weight(), r.kind().weighted(),
                r.origin().name(), r.enabled())).toList();
    }

    static CalendarEventKind toKind(CalendarKindJson kind) {
        return CalendarEventKind.valueOf(kind.getValue());
    }

    private static CalendarRowJson row(CalendarRow r, LocalDate today, boolean linkable) {
        CalendarEvent e = r.event();
        CalendarClassification c = r.classification();
        return new CalendarRowJson(e.id(), e.title(), e.location(), e.notes(), e.start().toString(),
                e.end().toString(), e.allDay(), kind(c.kind()), kind(c.suggested()), c.decidedByUser(),
                CalendarRowJson.StatusEnum.fromValue(c.status().name()), c.score(),
                c.reasons().stream().map(x -> new ReasonJson(x.why().name(), x.value(), x.weight(), x.text())).toList(),
                draft(r.draft()), match(r.match()), change(r.change()), r.removed(), r.missingFromCatalog(),
                r.needsAttention(), linkable,
                Arrays.stream(CalendarFilter.values()).filter(f -> f.on(today).test(r))
                        .map(f -> CalendarFilterJson.fromValue(f.name())).toList());
    }

    private static CalendarDraftJson draft(CalendarGigDraft d) {
        return new CalendarDraftJson(d.title(), d.date(), GigDraftMapping.text(d.showTime()),
                GigDraftMapping.text(d.eventStart()), d.venue(), d.city(), GigDraftMapping.toJson(d.country()),
                d.street(), d.postalCode());
    }

    private static CatalogMatchJson match(CatalogMatch m) {
        return new CatalogMatchJson(CatalogMatchJson.StateEnum.fromValue(m.state().name()), ref(m.gig()),
                m.sameDay().stream().map(CalendarMapping::ref).toList(),
                m.differences().stream().map(d -> new MatchDifferenceJson(
                        MatchDifferenceJson.KindEnum.fromValue(d.kind().name()), d.text(), d.calendar(), d.catalog()))
                        .toList());
    }

    private static CalendarChangeJson change(CalendarChange change) {
        return change == null ? null : new CalendarChangeJson(
                CalendarChangeJson.TypeEnum.fromValue(change.type().name()),
                change.fields().stream().map(Enum::name).sorted().map(CalendarChangeJson.FieldsEnum::fromValue).toList(),
                change.suggestedBefore() == null ? null : kind(change.suggestedBefore()), change.at());
    }

    private static GigRefJson ref(Gig gig) {
        return gig == null ? null : new GigRefJson(gig.id().token(), gig.title(),
                gig.schedule().start().toLocalDateTime().toString(), gig.location().city(), gig.cancelled());
    }

    private static CalendarKindJson kind(CalendarEventKind kind) {
        return CalendarKindJson.fromValue(kind.name());
    }
}
