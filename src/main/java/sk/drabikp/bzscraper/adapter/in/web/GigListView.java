package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.core.task.TaskExecutor;
import sk.drabikp.bzscraper.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.application.port.in.ExportGigsAsCsvUseCase;
import sk.drabikp.bzscraper.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.application.port.in.PublishGigsUseCase;
import sk.drabikp.bzscraper.application.port.in.ResyncGigUseCase;
import sk.drabikp.bzscraper.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.application.port.in.WithdrawGigsUseCase;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformResult;
import sk.drabikp.bzscraper.domain.model.Publication;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The gig catalog (DB source of truth): list stored gigs — with where each one is
 * published, linking to it there — and publish, export, edit, cancel/reactivate or
 * delete them.
 */
@Route("")
@PageTitle("Gig catalog")
public class GigListView extends VerticalLayout {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm");

    private final ListGigsUseCase listGigs;
    private final ListPublicationsUseCase listPublications;
    private final DeleteGigUseCase deleteGig;
    private final UpdateGigUseCase updateGig;
    private final CancelGigUseCase cancelGig;
    private final PublishGigsUseCase publishGigs;
    private final WithdrawGigsUseCase withdrawGigs;
    private final ResyncGigUseCase resyncGig;
    private final TaskExecutor taskExecutor;

    private final Grid<Gig> grid = new Grid<>();
    private Map<GigId, Map<Platform, Publication>> publications = Map.of();

    public GigListView(ListGigsUseCase listGigs, ListPublicationsUseCase listPublications,
                       DeleteGigUseCase deleteGig, UpdateGigUseCase updateGig,
                       CancelGigUseCase cancelGig, PublishGigsUseCase publishGigs,
                       WithdrawGigsUseCase withdrawGigs, ResyncGigUseCase resyncGig,
                       ExportGigsAsCsvUseCase exportCsv, TaskExecutor taskExecutor) {
        this.listGigs = listGigs;
        this.listPublications = listPublications;
        this.deleteGig = deleteGig;
        this.updateGig = updateGig;
        this.cancelGig = cancelGig;
        this.publishGigs = publishGigs;
        this.withdrawGigs = withdrawGigs;
        this.resyncGig = resyncGig;
        this.taskExecutor = taskExecutor;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        H1 title = new H1("Gig catalog");
        title.addClassNames(LumoUtility.FontSize.XLARGE);
        HorizontalLayout nav = new HorizontalLayout(
                new RouterLink("+ Add gig", AddGigView.class),
                new RouterLink("Import…", ImportView.class));
        nav.setSpacing(true);

        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(g -> g.schedule().start().format(WHEN)).setHeader("When").setAutoWidth(true);
        grid.addColumn(Gig::title).setHeader("Event").setAutoWidth(true);
        grid.addColumn(g -> g.location().displayVenue()).setHeader("Venue").setAutoWidth(true);
        grid.addColumn(g -> g.location().city()).setHeader("City").setAutoWidth(true);
        grid.addColumn(g -> g.admission().type()).setHeader("Entry").setAutoWidth(true);
        grid.addColumn(g -> g.cancelled() ? "cancelled" : "").setHeader("").setAutoWidth(true);
        grid.addComponentColumn(this::publishedOn).setHeader("Published on").setAutoWidth(true);
        grid.setSizeFull();
        grid.addItemDoubleClickListener(e -> openEditDialog(e.getItem()));

        CheckboxGroup<Platform> targets = new CheckboxGroup<>("Publish to");
        targets.setItems(Platform.values());
        targets.setItemLabelGenerator(PublishSummaries::label);
        targets.setValue(EnumSet.allOf(Platform.class));

        Button publish = primary("Publish selected", e -> onPublish((Button) e.getSource(), targets.getValue()));
        Button edit = tertiary("Edit", e -> {
            Set<Gig> sel = grid.asMultiSelect().getValue();
            if (sel.size() != 1) {
                Notification.show("Select exactly one gig to edit", 3000, Notification.Position.MIDDLE);
            } else {
                openEditDialog(sel.iterator().next());
            }
        });
        Button cancel = tertiary("Cancel", e -> onCancel());
        Button reactivate = tertiary("Reactivate", e -> onReactivate());
        Button resync = tertiary("Re-sync", e -> onResync());

        Anchor downloadLink = new Anchor();
        downloadLink.getElement().setAttribute("download", true);
        Button download = tertiary("Download CSV", e -> {
            downloadLink.setHref(csvHandler(exportCsv.csvForCatalog()));
            downloadLink.getElement().callJsFunction("click");
        });
        Button delete = new Button("Delete", e -> onDelete());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(targets, publish, edit, cancel, reactivate, resync,
                download, delete, downloadLink);
        actions.setAlignItems(FlexComponent.Alignment.END);
        actions.setSpacing(true);

        add(title, nav, actions, grid);
        setFlexGrow(1, grid);
        refresh();
    }

    private static Button primary(String text, com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> onClick) {
        Button b = new Button(text, onClick);
        b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return b;
    }

    private static Button tertiary(String text, com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> onClick) {
        Button b = new Button(text, onClick);
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return b;
    }

    private void refresh() {
        publications = listPublications.publicationsByGig();
        grid.setItems(listGigs.allGigs());
    }

    /** The platforms the gig is published on, each linking to the gig's page there. */
    private HorizontalLayout publishedOn(Gig gig) {
        HorizontalLayout cell = new HorizontalLayout();
        cell.setSpacing(true);
        for (Publication publication : publications.getOrDefault(gig.id(), Map.of()).values()) {
            PlatformLinks.Link link = PlatformLinks.of(publication, gig.cancelled());
            if (link.href() == null) {
                cell.add(new Span(link.text()));
            } else {
                Anchor anchor = new Anchor(link.href(), link.text() + " ↗");
                anchor.setTarget("_blank");
                anchor.getElement().setAttribute("rel", "noopener");
                cell.add(anchor);
            }
        }
        return cell;
    }

    private void openEditDialog(Gig gig) {
        GigForm form = new GigForm();
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
            updateGig.update(gig.id(), edited);
            dialog.close();
            refresh();
            if (edited.equals(gig)) {
                Notification.show("No changes", 3000, Notification.Position.BOTTOM_START);
                return;
            }
            // push the edit to any platform the gig was published to
            onPlatforms("Updated 1 gig.", () -> resyncGig.pushEdit(edited));
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
    }

    private void onReactivate() {
        List<Gig> cancelled = grid.asMultiSelect().getValue().stream().filter(Gig::cancelled).toList();
        if (cancelled.isEmpty()) {
            Notification.show("Select at least one cancelled gig", 3000, Notification.Position.MIDDLE);
            return;
        }
        cancelled.forEach(g -> cancelGig.reactivate(g.id())); // local, immediate
        refresh();
        // platforms still show the gig cancelled: replace it with the active version
        onPlatforms("Reactivated " + cancelled.size() + " gig(s).", () -> {
            List<PlatformResult> all = new ArrayList<>();
            cancelled.forEach(g -> all.addAll(resyncGig.reactivate(g.reactivate())));
            return all;
        });
    }

    /** Re-pushes the selected gigs as they are now — the retry after a failed update. */
    private void onResync() {
        Set<Gig> selected = grid.asMultiSelect().getValue();
        if (selected.isEmpty()) {
            Notification.show("Select at least one gig", 3000, Notification.Position.MIDDLE);
            return;
        }
        onPlatforms("Re-synced " + selected.size() + " gig(s).", () -> {
            List<PlatformResult> all = new ArrayList<>();
            selected.forEach(g -> all.addAll(resyncGig.pushEdit(g)));
            return all;
        });
    }

    private void onPublish(Button publish, Set<Platform> platforms) {
        Set<Gig> selected = grid.asMultiSelect().getValue();
        if (selected.isEmpty()) {
            Notification.show("Select at least one gig", 3000, Notification.Position.MIDDLE);
            return;
        }
        if (platforms.isEmpty()) {
            Notification.show("Select at least one platform", 3000, Notification.Position.MIDDLE);
            return;
        }
        List<Gig> gigs = List.copyOf(selected);
        UI ui = UI.getCurrent();
        publish.setEnabled(false);
        publish.setText("Publishing…");
        taskExecutor.execute(() -> {
            try {
                var results = publishGigs.publish(platforms, gigs);
                ui.access(() -> {
                    refresh();
                    PublishSummaries.show(results);
                    publish.setText("Publish selected");
                    publish.setEnabled(true);
                });
            } catch (RuntimeException ex) {
                ui.access(() -> {
                    Notification.show("Publish failed: " + ex.getMessage(), 6000, Notification.Position.MIDDLE);
                    publish.setText("Publish selected");
                    publish.setEnabled(true);
                });
            }
        });
    }

    private void onCancel() {
        Set<Gig> selected = grid.asMultiSelect().getValue();
        if (selected.isEmpty()) {
            Notification.show("Select at least one gig", 3000, Notification.Position.MIDDLE);
            return;
        }
        selected.forEach(g -> cancelGig.cancel(g.id())); // local, immediate
        refresh();
        // propagate the cancellation to any platform the gig was published to
        withdrawOnPlatforms(selected, WithdrawAction.CANCEL, "Cancelled");
    }

    private void onDelete() {
        Set<Gig> selected = grid.asMultiSelect().getValue();
        if (selected.isEmpty()) {
            Notification.show("Select at least one gig to delete", 3000, Notification.Position.MIDDLE);
            return;
        }
        UI ui = UI.getCurrent();
        taskExecutor.execute(() -> {
            List<PlatformResult> all = new ArrayList<>();
            for (Gig g : selected) {
                all.addAll(withdrawGigs.withdraw(g.id(), WithdrawAction.DELETE));
                deleteGig.delete(g.id()); // local removal after platform delete
            }
            ui.access(() -> {
                refresh();
                Notification.show("Deleted " + selected.size() + " gig(s). "
                        + platformSummary(all), 5000, Notification.Position.BOTTOM_START);
            });
        });
    }

    private void withdrawOnPlatforms(Set<Gig> gigs, WithdrawAction action, String localVerb) {
        onPlatforms(localVerb + " " + gigs.size() + " gig(s).", () -> {
            List<PlatformResult> all = new ArrayList<>();
            gigs.forEach(g -> all.addAll(withdrawGigs.withdraw(g.id(), action)));
            return all;
        });
    }

    /** Runs platform work off the UI thread, then reports the per-platform outcome. */
    private void onPlatforms(String localDone, Supplier<List<PlatformResult>> work) {
        UI ui = UI.getCurrent();
        taskExecutor.execute(() -> {
            List<PlatformResult> all = work.get();
            ui.access(() -> {
                refresh();
                Notification.show(localDone + " " + platformSummary(all), 5000, Notification.Position.BOTTOM_START);
            });
        });
    }

    private static String platformSummary(List<PlatformResult> results) {
        if (results.isEmpty()) {
            return "Not published to any platform.";
        }
        long ok = results.stream().filter(PlatformResult::succeeded).count();
        long failed = results.size() - ok;
        StringBuilder sb = new StringBuilder("Platforms: ").append(ok).append(" ok");
        if (failed > 0) {
            sb.append(", ").append(failed).append(" failed");
            results.stream().filter(r -> !r.succeeded()).map(PlatformResult::detail)
                    .filter(d -> d != null && !d.isBlank()).findFirst()
                    .ifPresent(d -> sb.append(" (").append(d).append(")"));
        }
        return sb.toString();
    }

    private static DownloadHandler csvHandler(String csv) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return DownloadHandler.fromInputStream(event ->
                new DownloadResponse(new ByteArrayInputStream(bytes), "gigs.csv", "text/csv", bytes.length));
    }
}
