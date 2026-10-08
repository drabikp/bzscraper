package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
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
import sk.drabikp.bzscraper.application.port.in.CalendarCatalogUseCase;
import sk.drabikp.bzscraper.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.application.port.in.FindPlacesUseCase;
import sk.drabikp.bzscraper.application.port.in.ReviewCalendarUseCase;
import sk.drabikp.bzscraper.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.domain.model.CalendarChange;
import sk.drabikp.bzscraper.domain.model.CalendarClassification;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.CalendarEventStatus;
import sk.drabikp.bzscraper.domain.model.CalendarOverview;
import sk.drabikp.bzscraper.domain.model.CalendarRow;
import sk.drabikp.bzscraper.domain.model.CatalogMatch;
import sk.drabikp.bzscraper.domain.model.CatalogMatch.Kind;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ProfileRule;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The band calendar, sorted: every event as gig / not sure / not a gig by the band
 * profile, with the reasons, and where it stands against the catalog. The page shows the
 * calendar as last read; "Read calendar" reads it again and marks what is new, changed or
 * gone. Gigs missing from the catalog are added from a pre-filled form or linked to the
 * catalog's gig; for linked gigs the page offers what the calendar's changes call for
 * (update, cancel, delete) — always the user's click, through the catalog, so the
 * platforms follow via the sync outbox. Never writes the calendar.
 */
@Route("calendar")
@PageTitle("Band calendar")
public class CalendarView extends VerticalLayout {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm");
    private static final DateTimeFormatter READ_AT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
    /** Gigs this recent that are missing from the catalog still need a look (e.g. last month's gigs). */
    private static final int RECENT_DAYS = 60;
    private static final String BUSY = "Being synced to a platform right now — wait until it finishes";
    private static final String ATTENTION = "Needs a look";
    private static final String MISSING = "Gigs not in the catalog";
    private static final String NOT_SURE = "Not sure";
    private static final String GIGS = "Gigs";
    private static final String NOT_GIGS = "Not gigs";
    private static final String DECIDED = "Decided by you";
    private static final String ALL = "All events";

    private final ReviewCalendarUseCase review;
    private final CalendarCatalogUseCase calendarCatalog;
    private final UpdateGigUseCase updateGig;
    private final CancelGigUseCase cancelGig;
    private final DeleteGigUseCase deleteGig;
    private final SyncLogUseCase syncLog;
    private final FindPlacesUseCase places;
    private final TaskExecutor taskExecutor;

    private final Div summary = new Div();
    private final Span lastRead = new Span();
    private final Grid<CalendarRow> grid = new Grid<>();
    private final Select<String> show = new Select<>();
    private final TextField search = new TextField();
    private final Details rules = new Details();
    private final Button linkSameDay = new Button();
    private final Button allSeen = new Button("Mark all changes seen");
    private List<CalendarRow> rows = List.of();

    public CalendarView(ReviewCalendarUseCase review, CalendarCatalogUseCase calendarCatalog, UpdateGigUseCase updateGig,
                        CancelGigUseCase cancelGig, DeleteGigUseCase deleteGig, SyncLogUseCase syncLog,
                        FindPlacesUseCase places, TaskExecutor taskExecutor) {
        this.review = review;
        this.calendarCatalog = calendarCatalog;
        this.updateGig = updateGig;
        this.cancelGig = cancelGig;
        this.deleteGig = deleteGig;
        this.syncLog = syncLog;
        this.places = places;
        this.taskExecutor = taskExecutor;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        H1 title = new H1("Band calendar");
        title.addClassNames(LumoUtility.FontSize.XLARGE);
        RouterLink back = new RouterLink("← Catalog", GigListView.class);
        Paragraph intro = new Paragraph("The band's calendar sorted into gigs and the rest, checked against the "
                + "catalog. Add the gigs the catalog is missing; when the calendar changes a gig, update, cancel or "
                + "delete it here and the platforms follow. The calendar itself is never changed.");
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
        lastRead.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        show.setLabel("Show");
        show.setItems(ATTENTION, MISSING, NOT_SURE, GIGS, NOT_GIGS, DECIDED, ALL);
        show.setValue(ATTENTION);
        show.addValueChangeListener(e -> applyFilter());
        search.setLabel("Search");
        search.setPlaceholder("title or place");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> applyFilter());

        linkSameDay.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        linkSameDay.getElement().setProperty("title",
                "Links each gig event to the catalog's gig that day, where there is exactly one");
        linkSameDay.addClickListener(e -> act(() -> {
            int linked = calendarCatalog.linkSameDayGigs();
            Notification.show("Linked " + linked + " event(s) to the catalog's gig that day", 4000,
                    Notification.Position.BOTTOM_START);
        }));
        allSeen.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        allSeen.addClickListener(e -> act(review::seenAll));

        HorizontalLayout controls = new HorizontalLayout(read, show, search, linkSameDay, allSeen);
        controls.setAlignItems(FlexComponent.Alignment.END);
        controls.setWrap(true);

        configureGrid();
        showRules();
        add(controls, lastRead, summary, grid, rules);
        setFlexGrow(1, grid);
        refresh();
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();
        grid.setMinHeight("32rem");
        grid.addColumn(r -> when(r.event())).setHeader("When").setAutoWidth(true).setFlexGrow(0)
                .setComparator(Comparator.comparing((CalendarRow r) -> r.event().start()));
        grid.addComponentColumn(this::eventCell).setHeader("Event").setFlexGrow(2);
        grid.addComponentColumn(this::resultCell).setHeader("Result").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::catalogCell).setHeader("Catalog").setFlexGrow(2);
        grid.addColumn(r -> r.classification().score() + " · " + r.classification().reasons().stream()
                        .map(reason -> reason.text() + (reason.weight() != 0 ? " " + signed(reason.weight()) : ""))
                        .collect(Collectors.joining("; ")))
                .setHeader("Why").setFlexGrow(3);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
    }

    private Component eventCell(CalendarRow r) {
        Div cell = new Div(new Span(r.event().title()));
        if (!r.event().location().isEmpty()) {
            cell.add(small(r.event().location(), LumoUtility.TextColor.SECONDARY));
        }
        if (r.change() != null) {
            cell.add(small(changeText(r), LumoUtility.TextColor.PRIMARY, LumoUtility.FontWeight.SEMIBOLD));
        }
        return cell;
    }

    private static String changeText(CalendarRow r) {
        CalendarChange change = r.change();
        String text = switch (change.type()) {
            case NEW -> "New in the calendar";
            case REMOVED -> "Gone from the calendar";
            case RETURNED -> "Back in the calendar";
            case CHANGED -> "Changed: " + change.fields().stream()
                    .map(f -> f.name().toLowerCase(Locale.ROOT)).sorted().collect(Collectors.joining(", "));
        };
        CalendarEventKind before = change.suggestedBefore();
        if (before != null && before != r.classification().suggested()) {
            text += " · was " + kindText(before).toLowerCase(Locale.ROOT);
        }
        return text + " (" + SyncLabels.when(change.at()) + ")";
    }

    private Component resultCell(CalendarRow r) {
        CalendarClassification c = r.classification();
        String text = kindText(c.kind());
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

    private Component catalogCell(CalendarRow r) {
        CatalogMatch match = r.match();
        Div cell = new Div();
        switch (match.state()) {
            case LINKED -> {
                cell.add(new Span("✓ " + gigLabel(match.gig())));
                match.differences().forEach(d -> cell.add(small("⚠ " + d.text(), LumoUtility.TextColor.WARNING)));
            }
            case SAME_DAY -> {
                if (r.classification().kind() != CalendarEventKind.NOT_GIG) {
                    cell.add(small(match.gig() != null ? "Same day in the catalog: " + gigLabel(match.gig())
                            : match.sameDay().size() + " catalog gigs that day", LumoUtility.TextColor.SECONDARY));
                }
            }
            case MISSING -> {
                if (r.missingFromCatalog()) {
                    cell.add(small("Not in the catalog", LumoUtility.TextColor.WARNING));
                }
            }
        }
        return cell;
    }

    private Component actions(CalendarRow r) {
        String id = r.eventId();
        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setSpacing(false);
        buttons.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-xs)");
        CatalogMatch match = r.match();

        if (match.state() == CatalogMatch.State.LINKED) {
            Gig gig = match.gig();
            if (match.has(Kind.DATE) || match.has(Kind.SHOW_TIME)) {
                buttons.add(button("Update gig…", ButtonVariant.LUMO_PRIMARY, () -> openUpdateDialog(r, gig)));
            }
            if ((match.has(Kind.CANCELLED) || match.has(Kind.REMOVED)) && !gig.cancelled()) {
                buttons.add(button("Cancel gig", ButtonVariant.LUMO_ERROR, () -> onCancel(r, gig)));
            }
            if (match.has(Kind.REMOVED)) {
                buttons.add(button("Delete gig…", ButtonVariant.LUMO_ERROR, () -> confirmDelete(r, gig)));
                buttons.add(button("Keep gig", ButtonVariant.LUMO_TERTIARY, () -> act(() -> review.seen(id))));
            }
        } else if (r.missingFromCatalog() && r.classification().status() != CalendarEventStatus.CANCELLED) {
            buttons.add(button("Add to catalog…", ButtonVariant.LUMO_PRIMARY, () -> openAddDialog(r)));
            if (match.state() == CatalogMatch.State.SAME_DAY) {
                buttons.add(button("Link…", ButtonVariant.LUMO_TERTIARY, () -> openLinkDialog(r)));
            }
        }

        if (!r.removed()) {
            CalendarClassification c = r.classification();
            if (c.decidedByUser()) {
                Button undo = button("Undo", ButtonVariant.LUMO_TERTIARY, () -> act(() -> review.forget(id)));
                undo.getElement().setProperty("title", "Forget your answer; the rules decide again");
                buttons.add(undo);
            } else {
                buttons.add(button("Gig", c.kind() == CalendarEventKind.GIG
                                ? ButtonVariant.LUMO_TERTIARY : ButtonVariant.LUMO_SUCCESS,
                        () -> act(() -> review.decide(id, CalendarEventKind.GIG))));
                buttons.add(button("Not a gig", ButtonVariant.LUMO_TERTIARY,
                        () -> act(() -> review.decide(id, CalendarEventKind.NOT_GIG))));
            }
        }
        if (match.state() == CatalogMatch.State.LINKED && !r.removed()) {
            Button unlink = button("Unlink", ButtonVariant.LUMO_TERTIARY, () -> act(() -> calendarCatalog.unlink(id)));
            unlink.getElement().setProperty("title", "This event is not that catalog gig");
            buttons.add(unlink);
        }
        if (r.change() != null && !match.has(Kind.REMOVED)) {
            buttons.add(button(r.removed() ? "OK" : "Seen", ButtonVariant.LUMO_TERTIARY, () -> act(() -> review.seen(id))));
        }
        return buttons;
    }

    private void openAddDialog(CalendarRow r) {
        GigForm form = new GigForm(places);
        form.prefill(r.draft());
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add to catalog");
        dialog.setWidth("44rem");
        Paragraph hint = new Paragraph("Filled in from the calendar event — check it. The gig is saved to the "
                + "catalog only; publish it from the catalog when it's ready.");
        hint.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        dialog.add(hint, form);
        if (!r.event().notes().isEmpty()) {
            Pre notes = new Pre(r.event().notes());
            notes.getStyle().set("white-space", "pre-wrap").set("margin", "0");
            Details privateNotes = new Details("Calendar notes — private, not copied to the gig", notes);
            privateNotes.setWidthFull();
            dialog.add(privateNotes);
        }
        Button save = new Button("Save to catalog", e -> {
            String error = form.validationError();
            if (error != null) {
                Notification.show(error, 3000, Notification.Position.MIDDLE);
                return;
            }
            try {
                calendarCatalog.addToCatalog(r.eventId(), form.toGig());
            } catch (IllegalStateException ex) {
                Notification.show(ex.getMessage(), 6000, Notification.Position.MIDDLE);
                return;
            }
            dialog.close();
            refresh();
            Notification.show("Added to the catalog — publish it from the catalog when it's ready", 5000,
                    Notification.Position.BOTTOM_START);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
    }

    private void openLinkDialog(CalendarRow r) {
        Select<Gig> pick = new Select<>();
        pick.setLabel("The catalog's gig");
        pick.setItems(r.match().sameDay());
        pick.setItemLabelGenerator(CalendarView::gigLabel);
        pick.setValue(r.match().sameDay().getFirst());
        pick.setWidthFull();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Link \"" + r.event().title() + "\"");
        dialog.setWidth("32rem");
        dialog.add(new Paragraph("This calendar event is this catalog gig:"), pick);
        Button link = new Button("Link", e -> {
            dialog.close();
            act(() -> calendarCatalog.link(r.eventId(), pick.getValue().id()));
        });
        link.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), link);
        dialog.open();
    }

    private void openUpdateDialog(CalendarRow r, Gig gig) {
        if (busy(gig)) {
            return;
        }
        GigForm form = new GigForm(places);
        form.populate(gig);
        form.applyShow(r.draft().date(), r.draft().showTime(), r.match().has(Kind.DATE));
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Update gig from the calendar");
        dialog.setWidth("40rem");
        Paragraph hint = new Paragraph(r.match().differences().stream().map(CatalogMatch.Difference::text)
                .collect(Collectors.joining("; ")) + ". The calendar's day and show time are filled in (as the band's slot for an event over several days) — check them.");
        hint.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        dialog.add(hint, form);
        Button save = new Button("Save", e -> {
            String error = form.validationError();
            if (error != null) {
                Notification.show(error, 3000, Notification.Position.MIDDLE);
                return;
            }
            Gig edited = gig.cancelled() ? form.toGig().cancel() : form.toGig();
            dialog.close();
            QueueResult queued = updateGig.update(gig.id(), edited);
            refresh();
            SyncLabels.showQueued("Updated.", queued);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
    }

    private void onCancel(CalendarRow r, Gig gig) {
        if (busy(gig)) {
            return;
        }
        QueueResult queued = cancelGig.cancel(gig.id());
        review.seen(r.eventId());
        refresh();
        SyncLabels.showQueued("Cancelled.", queued);
    }

    private void confirmDelete(CalendarRow r, Gig gig) {
        if (busy(gig)) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Delete " + gigLabel(gig) + "?");
        dialog.add(new Paragraph("The gig is removed from the catalog and deleted on every platform it is on."));
        Button delete = new Button("Delete", e -> {
            dialog.close();
            QueueResult queued = deleteGig.delete(gig.id());
            review.seen(r.eventId());
            refresh();
            SyncLabels.showQueued("Deleted from the catalog.", queued);
        });
        delete.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(new Button("Keep it", e -> dialog.close()), delete);
        dialog.open();
    }

    private boolean busy(Gig gig) {
        List<SyncTask> tasks = syncLog.unfinished().stream().filter(t -> t.gigId().equals(gig.id())).toList();
        if (SyncLabels.running(tasks)) {
            Notification.show(BUSY, 4000, Notification.Position.MIDDLE);
            return true;
        }
        return false;
    }

    private void act(Runnable change) {
        try {
            change.run();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE);
        }
        refresh();
    }

    private void onRead(Button read) {
        UI ui = UI.getCurrent();
        read.setEnabled(false);
        read.setText("Reading…");
        taskExecutor.execute(() -> {
            try {
                CalendarOverview fresh = review.read();
                ui.access(() -> show(fresh));
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
        show(review.overview());
    }

    private void show(CalendarOverview overview) {
        rows = overview.rows().stream()
                .sorted(Comparator.comparing((CalendarRow r) -> r.event().start()).reversed())
                .toList();
        Instant at = overview.lastRead();
        lastRead.setText(at == null ? "The calendar hasn't been read yet — click Read calendar."
                : "Last read " + READ_AT.format(at.atZone(ZoneId.systemDefault())));
        long gigs = count(r -> r.classification().kind() == CalendarEventKind.GIG && !r.removed());
        long missing = count(r -> r.missingFromCatalog() && recent(r));
        long changes = count(r -> r.change() != null);
        long differences = count(r -> !r.match().differences().isEmpty());
        long linkable = count(r -> r.missingFromCatalog() && r.match().gig() != null);
        summary.removeAll();
        summary.add(new Paragraph(count(r -> !r.removed()) + " events · " + gigs + " gigs · "
                + count(r -> r.classification().kind() == CalendarEventKind.UNSURE && !r.removed()) + " not sure · "
                + missing + " recent or upcoming gigs not in the catalog · " + changes + " unseen changes · "
                + differences + " gigs the calendar changed · "
                + count(r -> r.classification().decidedByUser()) + " decided by you"));
        linkSameDay.setText("Link " + linkable + " gig(s) to the catalog's gig that day");
        linkSameDay.setVisible(linkable > 0);
        allSeen.setVisible(changes > 0);
        grid.setItems(rows);
        applyFilter();
    }

    private void applyFilter() {
        Predicate<CalendarRow> byShow = switch (show.getValue()) {
            case MISSING -> CalendarRow::missingFromCatalog;
            case NOT_SURE -> r -> r.classification().kind() == CalendarEventKind.UNSURE && !r.removed();
            case GIGS -> r -> r.classification().kind() == CalendarEventKind.GIG;
            case NOT_GIGS -> r -> r.classification().kind() == CalendarEventKind.NOT_GIG;
            case DECIDED -> r -> r.classification().decidedByUser();
            case ALL -> r -> true;
            default -> r -> r.needsAttention() || (!r.removed()
                    && (recent(r) && r.missingFromCatalog() && r.classification().status() != CalendarEventStatus.CANCELLED
                    || upcoming(r) && r.classification().kind() == CalendarEventKind.UNSURE));
        };
        String text = ProfileRule.fold(search.getValue());
        Predicate<CalendarRow> bySearch = text.isEmpty() ? r -> true
                : r -> ProfileRule.fold(r.event().title() + " " + r.event().location()).contains(text);
        grid.getListDataView().setFilter(byShow.and(bySearch)::test);
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
        table.addColumn(r -> r.origin().name().toLowerCase(Locale.ROOT) + (r.enabled() ? "" : " (off)"))
                .setHeader("From").setAutoWidth(true).setFlexGrow(0);
        table.setItems(all);
        Paragraph help = new Paragraph("Shipped rules come with the app; your band's own rules (e.g. members' "
                + "names) are read from the band profile file on start. Score ≥ gig threshold → gig, "
                + "≤ not-a-gig threshold → not a gig, between → not sure.");
        help.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        rules.setSummaryText("Band rules (" + all.size() + ")");
        rules.add(help, table);
        rules.setWidthFull();
    }

    private long count(Predicate<CalendarRow> test) {
        return rows.stream().filter(test).count();
    }

    private static boolean upcoming(CalendarRow r) {
        return !r.draft().date().isBefore(LocalDate.now());
    }

    private static boolean recent(CalendarRow r) {
        return !r.draft().date().isBefore(LocalDate.now().minusDays(RECENT_DAYS));
    }

    private static Button button(String text, ButtonVariant variant, Runnable onClick) {
        Button b = new Button(text, e -> onClick.run());
        b.addThemeVariants(ButtonVariant.LUMO_SMALL, variant);
        return b;
    }

    private static Span small(String text, String... classes) {
        Span span = new Span(text);
        span.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Display.BLOCK);
        span.addClassNames(classes);
        return span;
    }

    private static String gigLabel(Gig gig) {
        return DAY.format(gig.schedule().start()) + " · " + gig.title() + " · " + gig.location().city()
                + (gig.cancelled() ? " (cancelled)" : "");
    }

    private static String kindText(CalendarEventKind kind) {
        return switch (kind) {
            case GIG -> "Gig";
            case UNSURE -> "Not sure";
            case NOT_GIG -> "Not a gig";
        };
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
