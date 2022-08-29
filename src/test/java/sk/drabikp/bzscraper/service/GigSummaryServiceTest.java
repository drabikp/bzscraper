package sk.drabikp.bzscraper.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.drabikp.bzscraper.gigsummary.GigSummary;

import java.util.List;

@SpringBootTest
public class GigSummaryServiceTest {

    @Autowired
    private GigSummaryService gigSummaryService;

    @Test
    public void test01() {
        List<GigSummary> eufory = gigSummaryService.getAllGigsForBand("eufory");
        eufory.forEach(System.out::println);
    }
}
