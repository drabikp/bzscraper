package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.core.task.TaskExecutor;
import sk.drabikp.bzscraper.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.domain.model.CalendarClassification;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.CalendarEventStatus;
import sk.drabikp.bzscraper.domain.model.ProfileRule;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The band calendar, sorted: every event as gig / not sure / not a gig by the band
 * profile, with the reasons. The user settles "not sure" events and corrects mistakes;
 * each verdict is remembered for that event. Read-only towards the calendar.
 */
@Route("calendar")
@PageTitle("Band calendar")
public class CalendarView extends VerticalLayout {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm");
    private static final String NOT_SURE = "Not sure";
    private static final String GIGS = "Gigs";
    private static final String NOT_GIGS = "Not gigs";
    private static final String DECIDED = "Decided by you";
    private static final String ALL = "All events";

    private final ReviewCalendarUseCase review;
    private final TaskExecutor taskExecutor;

    private final Div summary = new Div();
    private final Grid<CalendarClassification> grid = new Grid<>();
    private final Select<String> show = new Select<>();
    private final TextField search = new TextField();
    private final Details rules = new Details();
    private List<CalendarEvent> events = List.of();
    private boolean loaded;

    public CalendarView(ReviewCalendarUseCase review, TaskExecutor taskExecutor) {
        this.review = review;
        this.taskExecutor = taskExecutor;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        H1 title = new H1("Band calendar");
        title.addClassNames(LumoUtility.FontSize.XLARGE);
        RouterLink back = new RouterLink("← Catalog", GigListView.class);
        Paragraph intro = new Paragraph("Every event in the band's calendar, sorted by the band's rules into gigs, "
                + "not sure and not gigs. Settle the \"not sure\" ones and correct mistakes — each answer is "
                + "remembered for that event. The calendar itself is never changed.");
        intro.addClassNames(LumoUtility.TextColor.SECONDARY);
        add(title, back, intro);

        if (!review.configured()) {
            add(new Paragraph("No calendar is configured. Set bzscraper.calendar.ical-url to the calendar's "
                    + "private iCal address (Google: calendar settings → Secret address in iCal format)."));
            return;
        }

        Button read = new Button("Read calendar");
        read.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        read.addClickListener(e -> onRead(read));

        show.setLabel("Show");
        show.setItems(NOT_SURE, GIGS, NOT_GIGS, DECIDED, ALL);
        show.setValue(NOT_SURE);
        show.addValueChangeListener(e -> applyFilter());
        search.setLabel("Search");
        search.setPlaceholder("title or place");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> applyFilter());
        HorizontalLayout controls = new HorizontalLayout(read, show, search);
        controls.setAlignItems(FlexComponent.Alignment.END);

        configureGrid();
        showRules();
        add(controls, summary, grid, rules);
        setFlexGrow(1, grid);
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();
        grid.addColumn(c -> when(c.event())).setHeader("When").setAutoWidth(true).setFlexGrow(0)
                .setComparator(Comparator.comparing((CalendarClassification c) -> c.event().start()));
        grid.addComponentColumn(c -> {
            Div cell = new Div(new Span(c.event().title()));
            if (!c.event().location().isEmpty()) {
                Span place = new Span(c.event().location());
                place.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL,
                        LumoUtility.Display.BLOCK);
                cell.add(place);
            }
            return cell;
        }).setHeader("Event").setFlexGrow(2);
        grid.addComponentColumn(this::resultCell).setHeader("Result").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(CalendarClassification::score).setHeader("Score").setAutoWidth(true).setFlexGrow(0)
                .setSortable(true);
        grid.addColumn(c -> c.reasons().stream()
                        .map(r -> r.text() + (r.weight() != 0 ? " " + signed(r.weight()) : ""))
                        .collect(Collectors.joining("; ")))
                .setHeader("Why").setFlexGrow(3);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
    }

    private Component resultCell(CalendarClassification c) {
        String text = switch (c.kind()) {
            case GIG -> "Gig";
            case UNSURE -> "Not sure";
            case NOT_GIG -> "Not a gig";
        };
        if (c.kind() != CalendarEventKind.NOT_GIG && c.status() != CalendarEventStatus.CONFIRMED) {
            text += c.status() == CalendarEventStatus.CANCELLED ? " · cancelled" : " · tentative";
        }
        Span result = new Span(text + (c.decidedByUser() ? " (you)" : ""));
        result.addClassNames(switch (c.kind()) {
            case GIG -> LumoUtility.TextColor.SUCCESS;
            case UNSURE -> LumoUtility.TextColor.WARNING;
            case NOT_GIG -> LumoUtility.TextColor.SECONDARY;
        });
        return result;
    }

    private Component actions(CalendarClassification c) {
        String id = c.event().id();
        if (c.decidedByUser()) {
            Button undo = new Button("Undo", e -> decide(() -> review.forget(id)));
            undo.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            undo.getElement().setProperty("title", "Forget your answer; the rules decide again");
            return undo;
        }
        Button gig = new Button("Gig", e -> decide(() -> review.decide(id, CalendarEventKind.GIG)));
        Button notGig = new Button("Not a gig", e -> decide(() -> review.decide(id, CalendarEventKind.NOT_GIG)));
        gig.addThemeVariants(ButtonVariant.LUMO_SMALL, c.kind() == CalendarEventKind.GIG
                ? ButtonVariant.LUMO_TERTIARY : ButtonVariant.LUMO_SUCCESS);
        notGig.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        return new HorizontalLayout(gig, notGig);
    }

    private void decide(Runnable verdict) {
        verdict.run();
        refresh();
    }

    private void onRead(Button read) {
        UI ui = UI.getCurrent();
        read.setEnabled(false);
        read.setText("Reading…");
        taskExecutor.execute(() -> {
            try {
                List<CalendarEvent> fresh = review.read();
                ui.access(() -> {
                    events = fresh;
                    refresh();
                });
            } catch (CalendarUnavailableException | RuntimeException ex) {
                ui.access(() -> Notification.show("Reading the calendar failed: " + ex.getMessage(), 6000,
                        Notification.Position.MIDDLE));
            } finally {
                ui.access(() -> {
                    read.setText("Read calendar");
                    read.setEnabled(true);
                });
            }
        });
    }

    private void refresh() {
        List<CalendarClassification> results = review.classify(events).stream()
                .sorted(Comparator.comparing((CalendarClassification c) -> c.event().start()).reversed())
                .toList();
        long gigs = count(results, c -> c.kind() == CalendarEventKind.GIG);
        long tentative = count(results, c -> c.kind() == CalendarEventKind.GIG
                && c.status() == CalendarEventStatus.TENTATIVE);
        long cancelled = count(results, c -> c.kind() == CalendarEventKind.GIG
                && c.status() == CalendarEventStatus.CANCELLED);
        summary.removeAll();
        summary.add(new Paragraph(results.size() + " events · " + gigs + " gigs (" + tentative + " tentative, "
                + cancelled + " cancelled) · " + count(results, c -> c.kind() == CalendarEventKind.UNSURE)
                + " not sure · " + count(results, c -> c.kind() == CalendarEventKind.NOT_GIG) + " not gigs · "
                + count(results, CalendarClassification::decidedByUser) + " decided by you"));
        grid.setItems(results);
        loaded = true;
        applyFilter();
    }

    private void applyFilter() {
        if (!loaded) {
            return;
        }
        Predicate<CalendarClassification> byResult = switch (show.getValue()) {
            case GIGS -> c -> c.kind() == CalendarEventKind.GIG;
            case NOT_GIGS -> c -> c.kind() == CalendarEventKind.NOT_GIG;
            case DECIDED -> CalendarClassification::decidedByUser;
            case ALL -> c -> true;
            default -> c -> c.kind() == CalendarEventKind.UNSURE;
        };
        String text = ProfileRule.fold(search.getValue());
        Predicate<CalendarClassification> bySearch = text.isEmpty() ? c -> true
                : c -> ProfileRule.fold(c.event().title() + " " + c.event().location()).contains(text);
        grid.getListDataView().setFilter(byResult.and(bySearch)::test);
    }

    private void showRules() {
        List<ProfileRule> all = review.rules();
        Grid<ProfileRule> table = new Grid<>();
        table.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        table.setAllRowsVisible(true);
        table.addColumn(r -> r.kind().name()).setHeader("Rule").setAutoWidth(true).setFlexGrow(0);
        table.addColumn(r -> r.value() == null ? "" : r.value().replace("|", " | ")).setHeader("Value").setFlexGrow(1);
        table.addColumn(r -> r.kind().weighted() ? signed(r.weight()) : "—").setHeader("Weight")
                .setAutoWidth(true).setFlexGrow(0);
        table.addColumn(r -> r.origin().name().toLowerCase() + (r.enabled() ? "" : " (off)")).setHeader("From")
                .setAutoWidth(true).setFlexGrow(0);
        table.setItems(all);
        Paragraph help = new Paragraph("Shipped rules come with the app; your band's own rules (e.g. members' "
                + "names) are read from the band profile file on start. Score ≥ gig threshold → gig, "
                + "≤ not-a-gig threshold → not a gig, between → not sure.");
        help.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        rules.setSummaryText("Band rules (" + all.size() + ")");
        rules.add(help, table);
        rules.setWidthFull();
    }

    private static long count(List<CalendarClassification> results, Predicate<CalendarClassification> test) {
        return results.stream().filter(test).count();
    }

    private static String when(CalendarEvent event) {
        if (!event.allDay()) {
            return event.start().format(WHEN);
        }
        long days = ChronoUnit.DAYS.between(event.start().toLocalDate(), event.end().toLocalDate());
        return event.start().format(DAY) + (days > 1 ? " – " + event.end().minusDays(1).format(DAY) : "")
                + " (all day)";
    }

    private static String signed(int weight) {
        return weight > 0 ? "+" + weight : "−" + Math.abs(weight);
    }
}
