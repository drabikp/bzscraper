package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.core.task.TaskExecutor;
import sk.drabikp.bzscraper.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ImportDecision;
import sk.drabikp.bzscraper.domain.model.ImportPlan;
import sk.drabikp.bzscraper.domain.model.ImportProposal.Version;
import sk.drabikp.bzscraper.domain.model.ImportProposal;
import sk.drabikp.bzscraper.domain.model.ImportResult;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Platforms;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Initial import: reads the band's gigs (upcoming and past) from the chosen platforms
 * and proposes, per gig, to add it to the catalog or link it to the catalog gig it
 * already is — so existing events are managed from here instead of published again.
 * The user decides per row: import or not, whether a suggested match (same date and
 * city, venue written differently) is really one gig, and which version's details to
 * keep where the platforms disagree.
 */
@Route("import")
@PageTitle("Import gigs")
public class ImportView extends VerticalLayout {

    private final Clock clock;

    private final Platforms platforms;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm");
    private static final DateTimeFormatter END = DateTimeFormatter.ofPattern("d MMM HH:mm");
    private static final String ALL = "All gigs";
    private static final String NEEDS_DECISION = "Needs a decision";
    private static final String UPCOMING = "Upcoming only";

    private final ImportGigsUseCase importGigs;
    private final TaskExecutor taskExecutor;

    private final Div summary = new Div();
    private final Grid<ImportProposal> grid = new Grid<>();
    private final Select<String> show = new Select<>();
    private final Button apply = new Button("Import selected");
    private final Map<ImportProposal, Row> rows = new IdentityHashMap<>();

    /** The user's choices for one proposal, edited in the grid. */
    private static final class Row {
        boolean include = true;
        boolean sameGig;
        Version chosen;
    }

    public ImportView(ImportGigsUseCase importGigs, TaskExecutor taskExecutor, Platforms platforms, Clock clock) {
        this.clock = clock;
        this.platforms = platforms;
        this.importGigs = importGigs;
        this.taskExecutor = taskExecutor;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        H1 title = new H1("Import gigs");
        title.addClassNames(LumoUtility.FontSize.XLARGE);
        RouterLink back = new RouterLink("← Catalog", GigListView.class);
        Paragraph intro = new Paragraph("Bring the band's existing gigs — upcoming and past — from the platforms "
                + "into the catalog, linked to their events there, so they are edited and cancelled from here "
                + "instead of published again. Reading Bandsintown takes a minute or two.");
        intro.addClassNames(LumoUtility.TextColor.SECONDARY);

        CheckboxGroup<Platform> sources = new CheckboxGroup<>("Read from");
        sources.setItems(importGigs.importablePlatforms());
        sources.setItemLabelGenerator(this.platforms::name);
        sources.setValue(importGigs.importablePlatforms());
        Button read = new Button("Read platforms");
        read.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        read.addClickListener(e -> onRead(read, sources));
        HorizontalLayout controls = new HorizontalLayout(sources, read);
        controls.setAlignItems(FlexComponent.Alignment.END);

        show.setLabel("Show");
        show.setItems(ALL, NEEDS_DECISION, UPCOMING);
        show.setValue(ALL);
        show.addValueChangeListener(e -> applyFilter());

        configureGrid();

        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        apply.setEnabled(false);
        apply.addClickListener(e -> onApply());
        HorizontalLayout footer = new HorizontalLayout(show, apply);
        footer.setAlignItems(FlexComponent.Alignment.END);

        add(title, back, intro, controls, summary, footer, grid);
        setFlexGrow(1, grid);
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();
        grid.addComponentColumn(p -> {
            Checkbox include = new Checkbox(rows.get(p).include);
            include.addValueChangeListener(e -> rows.get(p).include = e.getValue());
            return include;
        }).setHeader("Import").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(p -> p.defaultVersion().gig().schedule().start().format(WHEN))
                .setHeader("When").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(p -> describe(p.defaultVersion().gig())).setHeader("Gig").setFlexGrow(1);
        grid.addColumn(p -> p.copies().stream().map(c -> platforms.name(c.platform()))
                .collect(Collectors.joining(" + "))).setHeader("Found on").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(p -> p.inCatalog() ? "links to an existing gig" : "new")
                .setHeader("Catalog").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::matchCell).setHeader("Same gig?").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::versionCell).setHeader("Keep details from").setFlexGrow(1);
    }

    private com.vaadin.flow.component.Component matchCell(ImportProposal p) {
        if (!p.suggested()) {
            return new Span(p.copies().size() > 1 || p.inCatalog() ? "same date & venue" : "");
        }
        Checkbox same = new Checkbox("same date & city — confirm", rows.get(p).sameGig);
        same.getElement().setProperty("title", "Ticked: one gig. Unticked: each copy is imported on its own.");
        same.addValueChangeListener(e -> rows.get(p).sameGig = e.getValue());
        return same;
    }

    private com.vaadin.flow.component.Component versionCell(ImportProposal p) {
        List<Version> versions = p.versions();
        if (versions.size() == 1) {
            return new Span("—");
        }
        ComboBox<Version> pick = new ComboBox<>();
        pick.setItems(versions);
        pick.setItemLabelGenerator(v -> v.source(platforms) + ": " + describe(v.gig()) + ", "
                + v.gig().schedule().start().format(WHEN)
                + (v.gig().schedule().end() != null ? "–" + v.gig().schedule().end().format(END) : ""));
        pick.setValue(rows.get(p).chosen);
        pick.setWidthFull();
        pick.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                rows.get(p).chosen = e.getValue();
            }
        });
        return pick;
    }

    private void onRead(Button read, CheckboxGroup<Platform> platforms) {
        if (platforms.getValue().isEmpty()) {
            Notification.show("Pick at least one platform", 3000, Notification.Position.MIDDLE);
            return;
        }
        UI ui = UI.getCurrent();
        read.setEnabled(false);
        read.setText("Reading…");
        apply.setEnabled(false);
        taskExecutor.execute(() -> {
            try {
                ImportPlan plan = importGigs.plan(platforms.getValue());
                ui.access(() -> showPlan(plan));
            } catch (RuntimeException ex) {
                String why = UserErrors.describe(ex);
                ui.access(() -> Notification.show("Reading failed: " + why, 6000, Notification.Position.MIDDLE));
            } finally {
                ui.access(() -> {
                    read.setText("Read platforms");
                    read.setEnabled(true);
                });
            }
        });
    }

    private void showPlan(ImportPlan plan) {
        rows.clear();
        for (ImportProposal p : plan.proposals()) {
            ImportDecision byDefault = ImportDecision.byDefault(p);
            Row row = new Row();
            row.include = byDefault.include();
            row.sameGig = byDefault.sameGig();
            row.chosen = byDefault.chosen();
            rows.put(p, row);
        }

        summary.removeAll();
        List<ImportProposal> proposals = plan.proposals();
        long added = proposals.stream().filter(p -> !p.inCatalog()).count();
        long suggested = proposals.stream().filter(ImportProposal::suggested).count();
        long conflicts = proposals.stream().filter(ImportProposal::hasConflict).count();
        summary.add(new Paragraph(added + " new · " + (proposals.size() - added) + " to link to catalog gigs · "
                + suggested + " suggested matches to confirm · " + conflicts + " with differing details · "
                + plan.alreadyLinked() + " already linked"));
        plan.failures().forEach((platform, reason) -> {
            Span failure = new Span(platforms.name(platform) + " could not be read: " + reason);
            failure.addClassNames(LumoUtility.TextColor.ERROR);
            summary.add(new Div(failure));
        });
        if (!plan.skipped().isEmpty()) {
            VerticalLayout list = new VerticalLayout();
            list.setPadding(false);
            plan.skipped().forEach(s -> list.add(new Span(s)));
            summary.add(new Details(plan.skipped().size() + " left out", list));
        }

        grid.setItems(new ArrayList<>(proposals));
        applyFilter();
        apply.setEnabled(!proposals.isEmpty());
    }

    private void applyFilter() {
        Predicate<ImportProposal> filter = switch (show.getValue()) {
            case NEEDS_DECISION -> p -> p.suggested() || p.hasConflict();
            case UPCOMING -> p -> !p.date().isBefore(LocalDate.now(clock));
            default -> p -> true;
        };
        grid.getListDataView().setFilter(filter::test);
    }

    private void onApply() {
        List<ImportDecision> decisions = new ArrayList<>();
        rows.forEach((p, row) -> decisions.add(new ImportDecision(p, row.include, row.sameGig, row.chosen)));
        if (decisions.stream().noneMatch(ImportDecision::include)) {
            Notification.show("Nothing selected to import", 3000, Notification.Position.MIDDLE);
            return;
        }
        ImportResult result = importGigs.apply(decisions);
        Notification.show("Imported: " + result.added() + " added, " + result.updated() + " updated, "
                        + result.linked() + " platform events linked"
                        + (result.skipped() > 0 ? ", " + result.skipped() + " skipped" : "") + ".",
                6000, Notification.Position.BOTTOM_START);
        UI.getCurrent().navigate(GigListView.class);
    }

    private static String describe(Gig gig) {
        return gig.title() + " — " + gig.location().displayVenue() + ", " + gig.location().city()
                + (gig.cancelled() ? " (cancelled)" : "");
    }
}
