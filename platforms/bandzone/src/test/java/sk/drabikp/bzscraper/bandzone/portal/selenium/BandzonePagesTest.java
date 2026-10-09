package sk.drabikp.bzscraper.bandzone.portal.selenium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoAlertPresentException;
import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;
import sk.drabikp.bzscraper.gig.domain.Address;
import sk.drabikp.bzscraper.gig.domain.Admission;
import sk.drabikp.bzscraper.gig.domain.Country;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigSchedule;
import sk.drabikp.bzscraper.gig.domain.Location;
import sk.drabikp.bzscraper.sync.application.port.out.FailureKind;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The Bandzone page objects against copies of the band admin's forms ({@link BandzoneFixture}):
 * the town picked among same-named towns by its district, the club linked or kept as text,
 * the stored concert read back, the wizard's new id, the delete tab without its confirm(),
 * the lineup tab's performers made the gig's lineup (the band itself kept, a profile or a stub).
 */
class BandzonePagesTest {

    private static final Address HRANICE_PREROV =
            new Address(null, "753 01", "okres Přerov", "Olomoucký kraj", 49.548, 17.735);

    private BandzoneFixture bandzone;

    @BeforeEach
    void start() throws Exception {
        bandzone = new BandzoneFixture();
        bandzone.concert("100", Map.of("name", "Old name", "cityId", "town-praha",
                "cityId__container[textInput]", "Praha", "start[date]", "1.9.2026", "start[time]", "20:00",
                "entryType", "2"));
    }

    @AfterEach
    void stop() {
        if (bandzone != null) {
            bandzone.close();
        }
    }

    private static Gig gig(String venue, Address address) {
        return Gig.create("Eufory + Snaefell",
                GigSchedule.startingAt(ZonedDateTime.of(2026, 9, 18, 21, 30, 0, 0, ZoneId.of("Europe/Prague"))),
                new Location(venue, "Hranice", Country.CZECHIA, address), List.of(), Admission.free(),
                null, null, null, null);
    }

    @Test
    void the_edit_form_picks_the_town_by_its_district_links_the_club_and_checks_what_was_stored() throws Exception {
        new BandzoneUpdateForm(bandzone.browser, "100").save(gig("Zámecký klub", HRANICE_PREROV));

        assertThat(bandzone.concerts.get("100")).containsEntry("cityId", "town-hranice-prerov")
                .containsEntry("venueId", "club-17")
                .containsEntry("name", "Eufory + Snaefell")
                .containsEntry("start[date]", "18.9.2026")
                .containsEntry("start[time]", "21:30")
                .containsEntry("entryType", "2");
    }

    @Test
    void a_club_bandzone_doesnt_list_is_kept_as_text() throws Exception {
        new BandzoneUpdateForm(bandzone.browser, "100").save(gig("Nový klub", HRANICE_PREROV));

        assertThat(bandzone.concerts.get("100")).containsEntry("venueId", "")
                .containsEntry("venueId__container[textInput]", "Nový klub");
    }

    @Test
    void a_town_bandzone_knows_several_times_is_for_the_user_to_pick_nothing_is_saved() {
        assertThatThrownBy(() -> new BandzoneUpdateForm(bandzone.browser, "100").save(gig("Zámecký klub", null)))
                .isInstanceOfSatisfying(BandzoneUploadException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FailureKind.NEEDS_USER);
                    assertThat(e.getMessage()).contains("several towns named Hranice", "okres Cheb");
                });
        assertThat(bandzone.posts).isEmpty();
    }

    @Test
    void the_wizard_answers_the_similar_concerts_question_and_reads_the_new_concerts_id() throws Exception {
        bandzone.similarConcerts = true;

        String id = new BandzoneCreateWizard(bandzone.browser).create(gig("Zámecký klub", HRANICE_PREROV));

        assertThat(id).isEqualTo("777");
        assertThat(bandzone.posts).extracting(BandzoneFixture.Post::path).containsExactly(
                "/koncert/zalozit.html?step=1", "/koncert/zalozit.html?step=similar", "/koncert/zalozit.html?step=2");
        assertThat(bandzone.posts.getFirst().fields()).containsEntry("cityId", "town-hranice-prerov")
                .containsEntry("start[date]", "18.9.2026");
        assertThat(bandzone.posts.getLast().fields()).containsEntry("name", "Eufory + Snaefell")
                .containsEntry("entryType", "2");
    }

    @Test
    void the_delete_tab_posts_the_pressed_button_without_its_confirm_dialog() {
        new BandzoneDeleteTab(bandzone.browser, "100").submit(BandzoneDeleteTab.CANCEL);

        assertThat(bandzone.posts).singleElement().satisfies(post -> {
            assertThat(post.path()).isEqualTo("/koncert/100/update?do=profileDeleteForm-submit");
            assertThat(post.fields()).containsEntry("cancelGig", "1").doesNotContainKey("delete");
        });
        assertThatThrownBy(() -> bandzone.driver.switchTo().alert()).isInstanceOf(NoAlertPresentException.class);
    }

    @Test
    void the_lineup_tab_removes_a_performer_not_in_the_lineup_but_never_the_band_itself() throws Exception {
        bandzone.performer("100", "Eufory");
        String oldBand = bandzone.performer("100", "Old Band");
        bandzone.performer("100", "Snaefell");

        new BandzoneLineupPage(bandzone.browser, "100").sync(List.of("Snaefell"), "eufory");

        assertThat(bandzone.lineups.get("100")).extracting(BandzoneFixture.Performer::name)
                .containsExactly("Eufory", "Snaefell");
        assertThat(bandzone.posts).extracting(BandzoneFixture.Post::path)
                .containsExactly("/koncert/100/update?do=gigBands-submit&em=delete&ei=" + oldBand);
    }

    @Test
    void a_lineup_name_matching_a_profile_whatever_its_case_is_added_as_that_profile() throws Exception {
        bandzone.performer("100", "Eufory");

        new BandzoneLineupPage(bandzone.browser, "100").sync(List.of("snaefell"), "Eufory");

        assertThat(bandzone.lineups.get("100")).extracting(BandzoneFixture.Performer::name, BandzoneFixture.Performer::profile)
                .containsExactly(tuple("Eufory", "band-1"), tuple("Snaefell", "band-11"));
        assertThat(bandzone.posts).singleElement().satisfies(post -> {
            assertThat(post.path()).isEqualTo("/koncert/100/update?do=addBandForm-submit");
            assertThat(post.fields()).containsEntry("bandIds[0]", "band-11").doesNotContainKey("bandIds[1]");
        });
    }

    @Test
    void a_lineup_name_no_profile_is_named_exactly_is_added_as_a_band_without_a_profile() throws Exception {
        bandzone.performer("100", "Eufory");

        new BandzoneLineupPage(bandzone.browser, "100").sync(List.of("Posterus"), "Eufory");

        assertThat(bandzone.lineups.get("100")).extracting(BandzoneFixture.Performer::name, BandzoneFixture.Performer::profile)
                .containsExactly(tuple("Eufory", "band-1"), tuple("Posterus", null));
        assertThat(bandzone.posts).extracting(BandzoneFixture.Post::path).containsExactly(
                "/koncert/100/update?do=addBandForm-bandIds-addCompleter-createItem",
                "/koncert/100/update?do=addBandForm-submit");
        assertThat(bandzone.posts.getFirst().fields()).containsEntry("bandIds__addCompleter__createItem[name]", "Posterus");
        assertThat(bandzone.posts.getLast().fields()).containsKey("bandIds[0]").doesNotContainKey("bandIds[1]");
    }

    @Test
    void a_lineup_already_on_the_tab_changes_nothing() throws Exception {
        bandzone.performer("100", "Eufory");
        bandzone.performer("100", "Posterus");
        bandzone.performer("100", "Snaefell");

        new BandzoneLineupPage(bandzone.browser, "100").sync(List.of("eufory", "SNAEFELL ", "posterus"), "Eufory");

        assertThat(bandzone.lineups.get("100")).extracting(BandzoneFixture.Performer::name)
                .containsExactly("Eufory", "Posterus", "Snaefell");
        assertThat(bandzone.posts).isEmpty();
    }
}
