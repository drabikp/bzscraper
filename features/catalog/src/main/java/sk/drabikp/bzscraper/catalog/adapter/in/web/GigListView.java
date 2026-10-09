package sk.drabikp.bzscraper.catalog.adapter.in.web;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.theme.lumo.LumoUtility;
import sk.drabikp.bzscraper.catalog.adapter.in.web.form.GigForm;
import sk.drabikp.bzscraper.catalog.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ExportGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.catalog.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.gig.application.ChangeListeners.Subscription;
import sk.drabikp.bzscraper.gig.application.UserFacingException;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.gig.domain.platform.Publication;
import sk.drabikp.bzscraper.places.application.port.in.FindPlacesUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.ResyncGigUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.WatchSyncUseCase;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncLabels;
import sk.drabikp.bzscraper.sync.domain.SyncStatus;
import sk.drabikp.bzscraper.sync.domain.SyncTask;
import sk.drabikp.bzscraper.ui.Notices;
import sk.drabikp.bzscraper.ui.UserErrors;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The gig catalog (DB source of truth): list stored gigs and publish, export, edit,
 * cancel/reactivate or delete them. Changes are made in the catalog at once and their
 * platform work goes to the sync outbox; the "Platforms" column shows, per platform, the
 * gig's page there and any sync still in progress (queued, running, retrying, failed),
 * live. While a gig's platform work is RUNNING, actions on that gig are blocked.
 */
@Route("")
@PageTitle("Gig catalog")
@Menu(title = "Catalog", order = 0, icon = "vaadin:list")
public class GigListView extends VerticalLayout {

    private final Platforms platforms;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm");
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("EEE d MMM HH:mm");
    private static final String BUSY = "Being synced to a platform right now — wait until it finishes";

    private final ListGigsUseCase listGigs;
    private final ListPublicationsUseCase listPublications;
    private final DeleteGigUseCase deleteGig;
    private final UpdateGigUseCase updateGig;
    private final CancelGigUseCase cancelGig;
    private final PublishGigsUseCase publishGigs;
    private final ResyncGigUseCase resyncGig;
    private final SyncLogUseCase syncLog;
    private final WatchSyncUseCase watchSync;
    private final FindPlacesUseCase places;

    private final Grid<Gig> grid = new Grid<>();
    private final Div syncSummary = new Div();
    private final List<Button> gigActions = new ArrayList<>();
    private Map<GigId, Map<Platform, Publication>> publications = Map.of();
    private Map<GigId, List<SyncTask>> unfinished = Map.of();
    private Subscription syncWatch;

    public GigListView(ListGigsUseCase listGigs, ListPublicationsUseCase listPublications,
                       DeleteGigUseCase deleteGig, UpdateGigUseCase updateGig,
                       CancelGigUseCase cancelGig, PublishGigsUseCase publishGigs,
                       ResyncGigUseCase resyncGig, SyncLogUseCase syncLog, WatchSyncUseCase watchSync,
                       ExportGigsUseCase export, FindPlacesUseCase places, Platforms platforms) {
        this.platforms = platforms;
        this.places = places;
        this.listGigs = listGigs;
        this.listPublications = listPublications;
        this.deleteGig = deleteGig;
        this.updateGig = updateGig;
        this.cancelGig = cancelGig;
        this.publishGigs = publishGigs;
        this.resyncGig = resyncGig;
        this.syncLog = syncLog;
        this.watchSync = watchSync;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        H1 title = new H1("Gig catalog");
        title.addClassNames(LumoUtility.FontSize.XLARGE);
        HorizontalLayout nav = new HorizontalLayout(new RouterLink("+ Add gig", AddGigView.class));
        nav.setSpacing(true);

        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(GigListView::when).setHeader("When").setAutoWidth(true);
        grid.addColumn(Gig::title).setHeader("Event").setAutoWidth(true);
        grid.addColumn(g -> g.location().displayVenue()).setHeader("Venue").setAutoWidth(true);
        grid.addColumn(g -> g.location().city()).setHeader("City").setAutoWidth(true);
        grid.addColumn(g -> g.admission().type()).setHeader("Entry").setAutoWidth(true);
        grid.addColumn(g -> g.cancelled() ? "cancelled" : "").setHeader("").setAutoWidth(true);
        grid.addComponentColumn(this::platformsCell).setHeader("Platforms").setAutoWidth(true);
        grid.setSizeFull();
        grid.addItemDoubleClickListener(e -> openEditDialog(e.getItem()));
        grid.asMultiSelect().addValueChangeListener(e -> updateActionState());

        CheckboxGroup<Platform> targets = new CheckboxGroup<>("Publish to");
        targets.setItems(platforms.all());
        targets.setItemLabelGenerator(platforms::name);
        targets.setValue(new HashSet<>(platforms.all()));

        Button publish = action("Publish selected", e -> onPublish(targets.getValue()));
        publish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button edit = action("Edit", e -> {
            Set<Gig> sel = grid.asMultiSelect().getValue();
            if (sel.size() != 1) {
                Notification.show("Select exactly one gig to edit", 3000, Notification.Position.MIDDLE);
            } else {
                openEditDialog(sel.iterator().next());
            }
        });
        Button cancel = action("Cancel", e -> onLifecycle(true));
        Button reactivate = action("Reactivate", e -> onLifecycle(false));
        Button resync = action("Re-sync", e -> onResync());
        Button delete = action("Delete", e -> onDelete());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);

        // a file to import by hand, for each platform that offers one
        Anchor downloadLink = new Anchor();
        downloadLink.getElement().setAttribute("download", true);
        List<Button> downloads = export.exportable().stream().map(platform -> {
            Button download = new Button("Download for " + platforms.name(platform), e ->
                    UserErrors.attempt(() -> export.export(platform)).ifPresent(file -> {
                        downloadLink.setHref(fileHandler(file));
                        downloadLink.getElement().callJsFunction("click");
                    }));
            download.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            return download;
        }).toList();

        HorizontalLayout actions = new HorizontalLayout(targets, publish, edit, cancel, reactivate, resync);
        downloads.forEach(actions::add);
        actions.add(delete, downloadLink);
        actions.setAlignItems(FlexComponent.Alignment.END);
        actions.setSpacing(true);

        syncSummary.addClassNames(LumoUtility.FontSize.SMALL);

        add(title, nav, actions, syncSummary, grid);
        setFlexGrow(1, grid);
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        UI ui = event.getUI();
        syncWatch = watchSync.watch(() -> ui.access(this::refreshSync));
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (syncWatch != null) {
            syncWatch.cancel();
        }
        super.onDetach(event);
    }

    /** A gig action: tertiary, and switched off while a selected gig is being synced. */
    private Button action(String text, ComponentEventListener<ClickEvent<Button>> onClick) {
        Button b = new Button(text, onClick);
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        gigActions.add(b);
        return b;
    }

    /** Reloads everything: after a change made on this page. */
    private void refresh() {
        loadSync();
        grid.setItems(listGigs.allGigs().stream()
                .sorted(Comparator.comparing((Gig g) -> g.schedule().start()).reversed())
                .toList());
        showSyncSummary();
        updateActionState();
    }

    /** Reloads only platform state, keeping the selection: when the sync worker moved on. */
    private void refreshSync() {
        loadSync();
        grid.getDataProvider().refreshAll();
        showSyncSummary();
        updateActionState();
    }

    private void loadSync() {
        publications = listPublications.publicationsByGig();
        unfinished = syncLog.unfinished().stream().collect(Collectors.groupingBy(SyncTask::gigId));
    }

    private void showSyncSummary() {
        List<SyncTask> all = unfinished.values().stream().flatMap(List::stream).toList();
        syncSummary.removeAll();
        if (all.isEmpty()) {
            Span ok = new Span("Platforms are in sync.");
            ok.addClassNames(LumoUtility.TextColor.SECONDARY);
            syncSummary.add(ok);
            return;
        }
        List<String> parts = new ArrayList<>();
        all.stream().filter(t -> t.status() == SyncStatus.RUNNING).forEach(t -> parts.add("↻ "
                + SyncLabels.ing(t.action()) + " " + t.gigLabel() + " on " + platforms.name(t.platform())));
        long queued = all.stream().filter(t -> t.status() == SyncStatus.PENDING && !t.retrying()).count();
        List<SyncTask> retrying = all.stream().filter(SyncTask::retrying).toList();
        long failed = all.stream().filter(t -> t.status() == SyncStatus.FAILED).count();
        if (queued > 0) {
            parts.add(queued + " queued");
        }
        if (!retrying.isEmpty()) {
            parts.add(retrying.size() + " failed, retrying from " + retrying.stream().map(SyncTask::nextAttemptAt)
                    .min(Comparator.naturalOrder()).map(SyncLabels::time).orElse(""));
        }
        if (failed > 0) {
            parts.add(failed + " failed — needs you");
        }
        Span text = new Span("Platform sync: " + String.join(" · ", parts) + " — see the Sync log");
        if (failed > 0) {
            text.addClassNames(LumoUtility.TextColor.ERROR);
        }
        syncSummary.add(text);
    }

    /** Per platform: the gig's page there, then any sync in progress for it. */
    private HorizontalLayout platformsCell(Gig gig) {
        HorizontalLayout cell = new HorizontalLayout();
        cell.setSpacing(true);
        Map<Platform, Publication> published = publications.getOrDefault(gig.id(), Map.of());
        Map<Platform, List<SyncTask>> tasks = unfinished.getOrDefault(gig.id(), List.of()).stream()
                .collect(Collectors.groupingBy(SyncTask::platform));
        // the registered platforms, and any other a record or task still mentions
        Set<Platform> shown = new LinkedHashSet<>(platforms.all());
        shown.addAll(published.keySet());
        shown.addAll(tasks.keySet());
        for (Platform platform : shown) {
            Publication publication = published.get(platform);
            List<SyncTask> platformTasks = tasks.getOrDefault(platform, List.of());
            if (publication == null && platformTasks.isEmpty()) {
                continue;
            }
            Div entry = new Div();
            if (publication != null) {
                PlatformLinks.Link link = PlatformLinks.of(platforms.traits(platform), publication, gig.cancelled());
                if (link.href() == null) {
                    entry.add(new Span(link.text()));
                } else {
                    Anchor anchor = new Anchor(link.href(), link.text() + " ↗");
                    anchor.setTarget("_blank");
                    anchor.getElement().setAttribute("rel", "noopener");
                    entry.add(anchor);
                }
            } else {
                entry.add(new Span(platforms.name(platform)));
            }
            for (SyncTask task : platformTasks) {
                Span state = new Span(" " + SyncLabels.state(task));
                state.addClassNames(LumoUtility.FontSize.XSMALL, task.status() == SyncStatus.FAILED
                        ? LumoUtility.TextColor.ERROR : LumoUtility.TextColor.SECONDARY);
                if (task.message() != null) {
                    state.getElement().setProperty("title", task.message());
                }
                entry.add(state);
            }
            cell.add(entry);
        }
        return cell;
    }

    private boolean busy(Gig gig) {
        return SyncLabels.running(unfinished.getOrDefault(gig.id(), List.of()));
    }

    private void updateActionState() {
        boolean busy = grid.asMultiSelect().getValue().stream().anyMatch(this::busy);
        for (Button b : gigActions) {
            b.setEnabled(!busy);
            b.getElement().setProperty("title", busy ? BUSY : "");
        }
    }

    /** The selection, or empty with a message; refuses gigs being synced right now. */
    private List<Gig> selected(String nothingSelected) {
        Set<Gig> sel = grid.asMultiSelect().getValue();
        if (sel.isEmpty()) {
            Notification.show(nothingSelected, 3000, Notification.Position.MIDDLE);
            return List.of();
        }
        if (sel.stream().anyMatch(this::busy)) {
            Notification.show(BUSY, 4000, Notification.Position.MIDDLE);
            return List.of();
        }
        return List.copyOf(sel);
    }

    private void openEditDialog(Gig gig) {
        if (busy(gig)) {
            Notification.show(BUSY, 4000, Notification.Position.MIDDLE);
            return;
        }
        GigForm form = new GigForm(places, platforms);
        form.populate(gig);
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit gig");
        dialog.setWidth("40rem");
        dialog.add(form);
        Button save = new Button("Save", e -> {
            String error = form.validationError();
            if (error != null) {
                Notification.show(error, 3000, Notification.Position.MIDDLE);
                return;
            }
            // the form edits details only; keep the cancelled state (Reactivate changes it)
            Gig edited = gig.cancelled() ? form.toGig().cancel() : form.toGig();
            dialog.close();
            if (edited.equals(gig)) {
                Notification.show("No changes", 3000, Notification.Position.BOTTOM_START);
                return;
            }
            Optional<QueueResult> queued = UserErrors.attempt(() -> updateGig.update(gig, edited));
            refresh();
            queued.ifPresent(q -> Notices.done(SyncLabels.queued("Updated.", q, platforms)));
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
    }

    private void onLifecycle(boolean cancel) {
        List<Gig> gigs = selected("Select at least one gig").stream()
                .filter(g -> g.cancelled() != cancel).toList();
        if (gigs.isEmpty()) {
            if (!grid.asMultiSelect().getValue().isEmpty()) {
                Notification.show(cancel ? "The selected gigs are already cancelled"
                        : "Select at least one cancelled gig", 3000, Notification.Position.MIDDLE);
            }
            return;
        }
        QueueResult queued = forEach(gigs, g -> cancel ? cancelGig.cancel(g.id()) : cancelGig.reactivate(g.id()));
        refresh();
        Notices.done(SyncLabels.queued((cancel ? "Cancelled " : "Reactivated ") + gigs.size() + " gig(s).", queued, platforms));
    }

    /** Re-pushes the selected gigs as they are now — e.g. after a platform copy was changed by hand. */
    private void onResync() {
        List<Gig> gigs = selected("Select at least one gig");
        if (gigs.isEmpty()) {
            return;
        }
        Optional<QueueResult> queued = UserErrors.attempt(() -> resyncGig.resync(gigs.stream().map(Gig::id).toList()));
        refresh();
        queued.ifPresent(q -> Notices.done(SyncLabels.queued("", q, platforms)));
    }

    private void onPublish(Set<Platform> targets) {
        List<Gig> gigs = selected("Select at least one gig");
        if (gigs.isEmpty()) {
            return;
        }
        if (targets.isEmpty()) {
            Notification.show("Select at least one platform", 3000, Notification.Position.MIDDLE);
            return;
        }
        Optional<QueueResult> queued = UserErrors.attempt(() -> publishGigs.publish(targets, gigs));
        refresh();
        queued.ifPresent(q -> Notices.done(SyncLabels.queued("", q, platforms)));
    }

    private void onDelete() {
        List<Gig> gigs = selected("Select at least one gig to delete");
        if (gigs.isEmpty()) {
            return;
        }
        QueueResult queued = forEach(gigs, g -> deleteGig.delete(g.id()));
        refresh();
        Notices.done(SyncLabels.queued("Deleted " + gigs.size() + " gig(s) from the catalog.", queued, platforms));
    }

    /** The change for each gig; one the catalog refuses (busy, changed meanwhile) is listed as not done. */
    private static QueueResult forEach(List<Gig> gigs, Function<Gig, QueueResult> change) {
        List<SyncTask> queued = new ArrayList<>();
        List<String> notQueued = new ArrayList<>();
        for (Gig gig : gigs) {
            try {
                QueueResult one = change.apply(gig);
                queued.addAll(one.queued());
                notQueued.addAll(one.notQueued());
            } catch (UserFacingException refused) {
                notQueued.add(refused.getMessage());
            }
        }
        return new QueueResult(queued, notQueued);
    }

    private static DownloadHandler fileHandler(ExportGigsUseCase.ExportFile file) {
        byte[] bytes = file.content().getBytes(StandardCharsets.UTF_8);
        return DownloadHandler.fromInputStream(event ->
                new DownloadResponse(new ByteArrayInputStream(bytes), file.fileName(), file.mediaType(), bytes.length));
    }

    /** The event's start (and last day when it runs over several), and the band's slot when given. */
    private static String when(Gig gig) {
        GigSchedule schedule = gig.schedule();
        String text = schedule.start().format(WHEN);
        if (schedule.multiDay()) {
            text += " – " + schedule.end().format(UNTIL);
        }
        if (schedule.hasSlot()) {
            text += " · band plays " + schedule.slot().start().format(SLOT);
        }
        return text;
    }
}
