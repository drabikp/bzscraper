package sk.drabikp.bzscraper.adapter.in.web;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;
import sk.drabikp.bzscraper.application.port.in.FindPlacesUseCase;
import sk.drabikp.bzscraper.application.port.in.SaveGigUseCase;

/**
 * Enter one gig by hand and save it to the local catalog (the source of truth).
 * Publishing to platforms happens later from the catalog, not here.
 */
@Route("add")
@PageTitle("Add gig")
public class AddGigView extends VerticalLayout {

    public AddGigView(SaveGigUseCase saveGig, FindPlacesUseCase places) {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        addClassNames(LumoUtility.Padding.LARGE);

        VerticalLayout card = new VerticalLayout();
        card.setWidth("40rem");
        card.setMaxWidth("100%");
        card.setPadding(true);
        card.setSpacing(true);
        card.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.LARGE,
                LumoUtility.BoxShadow.SMALL);

        H1 title = new H1("Add gig");
        title.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.Margin.Bottom.NONE);
        Paragraph hint = new Paragraph("Save a gig to your local catalog. Publish it to platforms "
                + "later from the catalog.");
        hint.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.Top.NONE);
        RouterLink back = new RouterLink("← Catalog", GigListView.class);

        GigForm form = new GigForm(places);
        form.setWidthFull();

        Button save = new Button("Save gig");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> {
            String error = form.validationError();
            if (error != null) {
                Notification.show(error, 3000, Notification.Position.MIDDLE);
                return;
            }
            if (UserErrors.attempt(() -> saveGig.add(form.toGig()))) {
                Notification.show("Gig saved to your catalog", 3000, Notification.Position.BOTTOM_START);
                form.clear();
            }
        });

        HorizontalLayout actions = new HorizontalLayout(save);
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        actions.setWidthFull();

        card.add(title, hint, back, form, actions);
        add(card);
    }
}
