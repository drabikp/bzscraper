package sk.drabikp.bzscraper.check.adapter.in.web;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.core.task.TaskExecutor;
import sk.drabikp.bzscraper.check.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.check.domain.Drift;
import sk.drabikp.bzscraper.check.domain.PlatformCheck;
import sk.drabikp.bzscraper.gig.application.ChangeListeners.Subscription;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.sync.application.port.in.ResyncGigUseCase;
import sk.drabikp.bzscraper.sync.application.port.in.WatchSyncUseCase;
import sk.drabikp.bzscraper.sync.domain.SyncLabels;
import sk.drabikp.bzscraper.ui.Notices;
import sk.drabikp.bzscraper.ui.UserErrors;

import java.util.List;

/**
 * Reconciliation: what the platforms show compared with the catalog. "Check now" reads them
 * (read-only, in the background — a portal can take a minute); the nightly check does the same.
 * Each difference has its fix: Re-sync (the catalog's details go to the platforms again) or,
 * for an event gone from a platform, Forget the link (Publish then creates it again).
 */
@Route("check")
@PageTitle("Platform check")
@Menu(title = "Platform check", order = 3, icon = "vaadin:check-square-o")
public class PlatformCheckView extends VerticalLayout {

    private final Platforms platforms;

    private final CheckPlatformsUseCase platformCheck;
    private final ResyncGigUseCase resync;
    private final TaskExecutor taskExecutor;
    private final WatchSyncUseCase watchSync;
    private final Button checkNow = new Button("Check now");
    private final Span status = new Span();
    private final VerticalLayout notes = new VerticalLayout();
    private final Grid<Drift> grid = new Grid<>();
    private Subscription syncWatch;
    private Subscription checkWatch;

    public PlatformCheckView(CheckPlatformsUseCase platformCheck, ResyncGigUseCase resync, TaskExecutor taskExecutor,
                             WatchSyncUseCase watchSync, Platforms platforms) {
        this.platforms = platforms;
        this.platformCheck = platformCheck;
        this.resync = resync;
        this.taskExecutor = taskExecutor;
        this.watchSync = watchSync;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);
        H1 title = new H1("Platform check");
        title.addClassNames(LumoUtility.FontSize.XLARGE);
        Paragraph intro = new Paragraph("The catalog is what should be on the platforms. This reads what they "
                + "really show — read-only — and lists the published gigs whose copy there differs: changed or "
                + "deleted there by hand, or placed in a town of the same name elsewhere. Gigs with platform work "
                + "still queued are left out. It also runs every night.");
        intro.addClassNames(LumoUtility.TextColor.SECONDARY);

        checkNow.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        checkNow.addClickListener(e -> onCheck());
        HorizontalLayout controls = new HorizontalLayout(checkNow, status);
        controls.setAlignItems(FlexComponent.Alignment.CENTER);
        notes.setPadding(false);
        notes.setSpacing(false);

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();
        grid.addColumn(Drift::gigLabel).setHeader("Gig").setFlexGrow(2);
        grid.addComponentColumn(this::platformCell).setHeader("Platform").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::differencesCell).setHeader("What differs").setFlexGrow(3);
        grid.addComponentColumn(this::fixCell).setHeader("").setAutoWidth(true).setFlexGrow(0);

        add(title, intro, controls, notes, grid);
        setFlexGrow(1, grid);
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        UI ui = event.getUI();
        syncWatch = watchSync.watch(() -> ui.access(this::refresh));
        checkWatch = platformCheck.watch(() -> ui.access(this::refresh));
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (syncWatch != null) {
            syncWatch.cancel();
            checkWatch.cancel();
        }
        super.onDetach(event);
    }

    private void onCheck() {
        UI ui = UI.getCurrent();
        checkNow.setEnabled(false);
        taskExecutor.execute(() -> {
            try {
                platformCheck.check();
            } catch (RuntimeException ex) {
                String why = UserErrors.describe(ex);
                ui.access(() -> Notification.show("The check failed: " + why, 6000, Notification.Position.MIDDLE));
            } finally {
                ui.access(this::refresh);
            }
        });
        refresh();
    }

    private void refresh() {
        boolean running = platformCheck.running();
        checkNow.setEnabled(!running);
        notes.removeAll();
        PlatformCheck check = platformCheck.lastCheck().orElse(null);
        if (running) {
            status.setText("Reading the platforms…");
        } else if (check == null) {
            status.setText("Not checked since the app started.");
        } else {
            status.setText("Checked " + Notices.when(check.checkedAt()) + " — "
                    + (check.drifts().isEmpty() ? "the platforms match the catalog."
                    : check.drifts().size() + " difference(s)."));
        }
        status.addClassNames(LumoUtility.TextColor.SECONDARY);
        if (check != null) {
            check.unreadable().forEach((platform, why) -> notes.add(note(platforms.name(platform)
                    + " couldn't be read: " + why, LumoUtility.TextColor.WARNING)));
            check.unlinked().forEach((platform, count) -> {
                if (count > 0) {
                    Span text = note(count + " event(s) on " + platforms.name(platform)
                            + " aren't linked to any catalog gig — Import brings them in.", LumoUtility.TextColor.SECONDARY);
                    notes.add(new Div(text));
                }
            });
        }
        grid.setItems(check == null ? List.of() : check.drifts());
    }

    private static Span note(String text, String color) {
        Span span = new Span(text);
        span.addClassNames(color);
        return span;
    }

    private Component platformCell(Drift drift) {
        Anchor link = new Anchor(platforms.traits(drift.platform()).eventUrl(drift.externalRef()),
                platforms.name(drift.platform()) + " ↗");
        link.setTarget("_blank");
        return link;
    }

    private Component differencesCell(Drift drift) {
        if (drift.kind() == Drift.Kind.MISSING) {
            return note("gone from " + platforms.name(drift.platform()) + " (deleted there?)",
                    LumoUtility.TextColor.ERROR);
        }
        VerticalLayout lines = new VerticalLayout();
        lines.setPadding(false);
        lines.setSpacing(false);
        drift.differences().forEach(d -> lines.add(new Span(d)));
        return lines;
    }

    private Component fixCell(Drift drift) {
        Button fix;
        if (drift.kind() == Drift.Kind.MISSING) {
            fix = new Button("Forget link", e -> {
                if (UserErrors.attempt(() -> platformCheck.forget(drift.platform(), drift.gigId()))) {
                    Notification.show("Forgotten — publish the gig to " + platforms.name(drift.platform())
                            + " again from the catalog.", 5000, Notification.Position.BOTTOM_START);
                }
            });
            fix.getElement().setProperty("title", "The event is gone there: forget it, so Publish creates it again");
        } else {
            fix = new Button("Re-sync", e -> UserErrors.attempt(() -> resync.resync(List.of(drift.gigId())))
                    .ifPresent(queued -> {
                        platformCheck.dismiss(drift.platform(), drift.gigId());
                        Notices.done(SyncLabels.queued("Re-sync queued.", queued, platforms));
                    }));
            fix.getElement().setProperty("title", "Send the catalog's details to the platforms again");
        }
        fix.addThemeVariants(ButtonVariant.LUMO_SMALL);
        return fix;
    }
}
