package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.core.task.TaskExecutor;
import sk.drabikp.bzscraper.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.ReconciliationEntry;
import sk.drabikp.bzscraper.domain.model.ReconciliationResult;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Import gigs from a platform and reconcile against the local catalog. Shows what's
 * NEW (pick which to add) and what CONFLICTS (choose keep-local or take-imported);
 * MATCHED and LOCAL-ONLY are reported for awareness. Applying only ever adds/updates
 * the catalog — never deletes.
 */
@Route("import")
@PageTitle("Import gigs")
public class ImportView extends VerticalLayout {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final ImportGigsUseCase importGigs;
    private final TaskExecutor taskExecutor;

    private final Span summary = new Span();
    private final Grid<ReconciliationEntry> newGrid = new Grid<>();
    private final Grid<ReconciliationEntry> conflictGrid = new Grid<>();
    private final Map<GigId, Checkbox> takeImported = new HashMap<>();
    private final Button apply = new Button("Apply to catalog");

    public ImportView(ImportGigsUseCase importGigs, TaskExecutor taskExecutor) {
        this.importGigs = importGigs;
        this.taskExecutor = taskExecutor;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        H1 title = new H1("Import gigs");
        title.addClassNames(LumoUtility.FontSize.XLARGE);
        RouterLink back = new RouterLink("← Catalog", GigListView.class);

        ComboBox<Platform> platform = new ComboBox<>("Platform");
        platform.setItems(importGigs.importablePlatforms());
        platform.setItemLabelGenerator(PublishSummaries::label);
        importGigs.importablePlatforms().stream().findFirst().ifPresent(platform::setValue);

        Button reconcile = new Button("Reconcile");
        reconcile.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        reconcile.addClickListener(e -> onReconcile(reconcile, platform.getValue()));

        HorizontalLayout controls = new HorizontalLayout(platform, reconcile);
        controls.setDefaultVerticalComponentAlignment(FlexEnd());
        controls.setSpacing(true);

        newGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        newGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        newGrid.addColumn(e -> whenOf(e.imported())).setHeader("When").setAutoWidth(true);
        newGrid.addColumn(e -> e.imported().title()).setHeader("Event").setAutoWidth(true);
        newGrid.addColumn(e -> e.imported().location().displayVenue()).setHeader("Venue").setAutoWidth(true);
        newGrid.addColumn(e -> e.imported().location().city()).setHeader("City").setAutoWidth(true);
        newGrid.setAllRowsVisible(true);

        conflictGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        conflictGrid.addColumn(e -> whenOf(e.local())).setHeader("When").setAutoWidth(true);
        conflictGrid.addColumn(e -> describe(e.local())).setHeader("In catalog").setAutoWidth(true);
        conflictGrid.addColumn(e -> describe(e.imported())).setHeader("On platform").setAutoWidth(true);
        conflictGrid.addComponentColumn(e -> {
            Checkbox cb = new Checkbox();
            takeImported.put(e.id(), cb);
            return cb;
        }).setHeader("Take platform version").setAutoWidth(true);
        conflictGrid.setAllRowsVisible(true);

        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        apply.setEnabled(false);
        apply.addClickListener(e -> onApply());

        add(title, back, controls, summary,
                new H2("New"), newGrid, new H2("Conflicts"), conflictGrid, apply);
    }

    private static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment FlexEnd() {
        return com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END;
    }

    private void onReconcile(Button reconcile, Platform platform) {
        if (platform == null) {
            Notification.show("Pick a platform", 3000, Notification.Position.MIDDLE);
            return;
        }
        UI ui = UI.getCurrent();
        reconcile.setEnabled(false);
        reconcile.setText("Reconciling…");
        taskExecutor.execute(() -> {
            try {
                ReconciliationResult result = importGigs.reconcile(platform);
                ui.access(() -> {
                    showResult(result);
                    reconcile.setText("Reconcile");
                    reconcile.setEnabled(true);
                });
            } catch (RuntimeException ex) {
                ui.access(() -> {
                    Notification.show("Import failed: " + ex.getMessage(), 6000, Notification.Position.MIDDLE);
                    reconcile.setText("Reconcile");
                    reconcile.setEnabled(true);
                });
            }
        });
    }

    private void showResult(ReconciliationResult result) {
        summary.setText("New: " + result.added().size()
                + "   ·   Conflicts: " + result.conflicts().size()
                + "   ·   Matched: " + result.matched().size()
                + "   ·   Only in catalog: " + result.localOnly().size());
        newGrid.setItems(result.added());
        result.added().forEach(newGrid::select); // pre-select all new
        takeImported.clear();
        conflictGrid.setItems(result.conflicts());
        apply.setEnabled(!result.added().isEmpty() || !result.conflicts().isEmpty());
    }

    private void onApply() {
        List<Gig> toSave = new ArrayList<>();
        newGrid.asMultiSelect().getValue().forEach(e -> toSave.add(e.imported()));
        conflictGrid.getListDataView().getItems().forEach(e -> {
            Checkbox cb = takeImported.get(e.id());
            if (cb != null && Boolean.TRUE.equals(cb.getValue())) {
                toSave.add(e.imported());
            }
        });
        if (toSave.isEmpty()) {
            Notification.show("Nothing selected to import", 3000, Notification.Position.MIDDLE);
            return;
        }
        importGigs.apply(toSave);
        Notification.show("Imported " + toSave.size() + " gig(s) into the catalog", 4000,
                Notification.Position.BOTTOM_START);
        UI.getCurrent().navigate(GigListView.class);
    }

    private static String whenOf(Gig gig) {
        return gig.schedule().start().format(WHEN);
    }

    private static String describe(Gig gig) {
        return gig.title() + " @ " + gig.location().displayVenue() + " (" + gig.admission().type() + ")";
    }
}
