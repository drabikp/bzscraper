package sk.drabikp.bzscraper.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import sk.drabikp.bzscraper.csv.GigSummaryCsvConverter;
import sk.drabikp.bzscraper.gigsummary.GigSummaryDto;
import sk.drabikp.bzscraper.service.GigSummaryService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Route("")
//@CssImport(value = "./styles/shared-styles.css", themeFor = "vaadin-date-picker")
public class GigsDownloadView extends VerticalLayout {
    private final GigSummaryService gigSummaryService;

    private final GigSummaryCsvConverter gigSummaryCsvConverter;

    public GigsDownloadView(GigSummaryService gigSummaryService, GigSummaryCsvConverter gigSummaryCsvConverter) {
        this.gigSummaryService = gigSummaryService;
        this.gigSummaryCsvConverter = gigSummaryCsvConverter;

        TextField bandSlugField = new TextField("Band Slug");
        DatePicker startDatePicker = new DatePicker("Start Date");
        DatePicker endDatePicker = new DatePicker("End Date");

        Anchor downloadLink = new Anchor();

        Button downloadButton = new Button("Download Gigs");
        downloadButton.addClickListener(e -> {
            String bandSlug = bandSlugField.getValue();
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();

            if (bandSlug.isEmpty() || startDate == null || endDate == null) {
                Notification.show("Please fill in all fields", 3000, Notification.Position.MIDDLE);
                return;
            }

            // Fetch gigs and generate CSV
            GigSummaryDto gigSummaryDto = gigSummaryService.getGigsBetweenDates(bandSlug, startDate, endDate);
            String csvData = gigSummaryCsvConverter.convertToCsv(gigSummaryDto.gigs());

            // Create and download CSV file
            StreamResource resource = createCSVResource(csvData);


            downloadLink.setHref(resource);
            downloadLink.setId("dl-link");
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.getElement().callJsFunction("click");
            add();
            //getUI().ifPresent(ui -> ui.getPage().executeJs("window.open($0)", resource));
        });

        add(bandSlugField, startDatePicker, endDatePicker, downloadButton, downloadLink);
    }

    private StreamResource createCSVResource(String csvData) {
        return new StreamResource("gigs.csv", () -> new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8)));
    }
}
