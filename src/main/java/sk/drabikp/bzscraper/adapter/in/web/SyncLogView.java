package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility;
import sk.drabikp.bzscraper.application.port.in.PauseSyncUseCase;
import sk.drabikp.bzscraper.application.port.in.SyncLogUseCase;
import sk.drabikp.bzscraper.domain.model.SyncLogEntry;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.model.SyncTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The sync log: every platform task — queued with a catalog change, run by the sync
 * worker — newest first, with its outcome and history (click a row). Failed tasks wait
 * for the user: Retry (after checking the platform) or Discard. Live.
 */
@Route("sync")
@PageTitle("Sync log")
public class SyncLogView extends VerticalLayout {

    private static final int LIMIT = 500;
    private static final String NEEDS_YOU = "Needs you (failed)";
    private static final String UNFINISHED = "Not finished";
    private static final String ALL = "All (last " + LIMIT + ")";

    private final SyncLogUseCase syncLog;
    private final PauseSyncUseCase pause;
    private final SyncBroadcaster broadcaster;
    private final Button pauseButton = new Button();
    private final Span pausedNote = new Span();
    private final Grid<SyncTask> grid = new Grid<>();
    private final Select<String> show = new Select<>();
    private final Set<Long> expanded = new HashSet<>();
    private Registration syncRegistration;

    public SyncLogView(SyncLogUseCase syncLog, PauseSyncUseCase pause, SyncBroadcaster broadcaster) {
        this.syncLog = syncLog;
        this.pause = pause;
        this.broadcaster = broadcaster;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);
        H1 title = new H1("Sync log");
        title.addClassNames(LumoUtility.FontSize.XLARGE);
        Paragraph intro = new Paragraph("Every change to a gig that is on a platform is saved here together with "
                + "the change and carried out in the background, one at a time. Updates, cancellations and "
                + "deletions are retried automatically (after 1, 5 and 15 minutes); a failed publish is not — it "
                + "may have happened anyway, so check the platform, then Retry or Discard. Click a row for its history.");
        intro.addClassNames(LumoUtility.TextColor.SECONDARY);

        show.setLabel("Show");
        show.setItems(UNFINISHED, NEEDS_YOU, ALL);
        show.setValue(ALL);
        show.addValueChangeListener(e -> refresh());

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();
        grid.addColumn(t -> SyncLabels.when(t.createdAt())).setHeader("Queued").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(SyncTask::gigLabel).setHeader("Gig").setFlexGrow(2);
        grid.addColumn(t -> PublishSummaries.label(t.platform())).setHeader("Platform").setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(t -> t.action().verb()).setHeader("Action").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::statusCell).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(t -> t.message() == null ? "" : t.message()).setHeader("Last outcome").setFlexGrow(3);
        grid.addColumn(t -> SyncLabels.when(t.updatedAt())).setHeader("Updated").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
        grid.setDetailsVisibleOnClick(false);
        grid.addItemClickListener(e -> toggle(e.getItem()));
        grid.setItemDetailsRenderer(new ComponentRenderer<>(this::history));

        pauseButton.addClickListener(e -> {
            if (pause.paused()) {
                pause.resume();
            } else {
                pause.pause();
            }
            refresh();
        });
        pausedNote.addClassNames(LumoUtility.TextColor.WARNING, LumoUtility.FontWeight.SEMIBOLD);
        HorizontalLayout controls = new HorizontalLayout(show, pauseButton, pausedNote);
        controls.setAlignItems(FlexComponent.Alignment.END);

        add(title, new RouterLink("← Catalog", GigListView.class), intro, controls, grid);
        setFlexGrow(1, grid);
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        UI ui = event.getUI();
        syncRegistration = broadcaster.register(() -> ui.access(this::refresh));
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (syncRegistration != null) {
            syncRegistration.remove();
        }
        super.onDetach(event);
    }

    private void refresh() {
        boolean paused = pause.paused();
        pauseButton.setText(paused ? "Resume sync" : "Pause sync");
        pauseButton.getElement().setProperty("title", paused ? "Start the waiting work again"
                : "Finish the gig in progress, then start nothing new until resumed");
        pausedNote.setText(paused ? "Paused — the waiting work stays queued" : "");
        List<SyncTask> tasks = switch (show.getValue()) {
            case UNFINISHED -> syncLog.unfinished().stream().filter(t -> t.status().open()).toList().reversed();
            case NEEDS_YOU -> syncLog.unfinished().stream().filter(t -> t.status() == SyncStatus.FAILED)
                    .toList().reversed();
            default -> syncLog.recent(LIMIT);
        };
        grid.setItems(tasks);
        tasks.stream().filter(t -> expanded.contains(t.id())).forEach(t -> grid.setDetailsVisible(t, true));
    }

    private void toggle(SyncTask task) {
        if (!expanded.remove(task.id())) {
            expanded.add(task.id());
        }
        grid.setDetailsVisible(task, expanded.contains(task.id()));
    }

    private Component statusCell(SyncTask task) {
        Span status = new Span(SyncLabels.status(task));
        status.addClassNames(switch (task.status()) {
            case FAILED -> LumoUtility.TextColor.ERROR;
            case DONE -> LumoUtility.TextColor.SUCCESS;
            case RUNNING, PENDING -> LumoUtility.TextColor.WARNING;
            case DISCARDED -> LumoUtility.TextColor.SECONDARY;
        });
        return status;
    }

    private Component actions(SyncTask task) {
        HorizontalLayout buttons = new HorizontalLayout();
        if (task.status() == SyncStatus.FAILED || task.status() == SyncStatus.DISCARDED) {
            Button retry = new Button("Retry", e -> act(() -> syncLog.retry(task.id())));
            retry.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            buttons.add(retry);
        }
        if (task.status() == SyncStatus.FAILED || task.status() == SyncStatus.PENDING) {
            Button discard = new Button("Discard", e -> act(() -> syncLog.discard(task.id())));
            discard.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            discard.getElement().setProperty("title", "Give up on it: the platform is left as it is");
            buttons.add(discard);
        }
        return buttons;
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException | IllegalArgumentException e) {
            Notification.show(e.getMessage(), 4000, Notification.Position.MIDDLE);
        }
        refresh();
    }

    private Component history(SyncTask task) {
        Div lines = new Div();
        lines.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Padding.Horizontal.LARGE);
        for (SyncLogEntry entry : syncLog.log(task.id())) {
            Div line = new Div(new Span(SyncLabels.when(entry.at()) + "  " + entry.message()));
            lines.add(line);
        }
        return lines;
    }
}
