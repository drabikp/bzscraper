package sk.drabikp.bzscraper.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.domain.model.Address;
import sk.drabikp.bzscraper.domain.model.Admission;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigSchedule;
import sk.drabikp.bzscraper.domain.model.Slot;
import sk.drabikp.bzscraper.domain.model.Location;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaGigRepositoryTest {

    @Autowired
    private JpaGigRepository repository;

    private static Gig fullGig() {
        return Gig.create("The Legends Rock Fest",
                new GigSchedule(prague(2026, 9, 15, 20, 0), prague(2026, 9, 15, 23, 30)),
                new Location("Klub 007", "Hořice", Country.CZECHIA), List.of("Eufory", "Support"),
                Admission.paid("150 Kč"), "A fun night",
                "https://fb.example/e", "https://tickets.example", "https://img.example/p.jpg");
    }

    private static ZonedDateTime prague(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of("Europe/Prague"));
    }

    @Test
    void round_trips_a_full_gig_through_the_database() {
        Gig gig = fullGig();

        repository.save(gig);
        Optional<Gig> found = repository.findById(gig.id());

        assertThat(found).isPresent();
        Gig reloaded = found.get();
        assertThat(reloaded.title()).isEqualTo("The Legends Rock Fest");
        assertThat(reloaded.schedule().start()).isEqualTo(prague(2026, 9, 15, 20, 0));
        assertThat(reloaded.schedule().end()).isEqualTo(prague(2026, 9, 15, 23, 30));
        assertThat(reloaded.location().venue()).isEqualTo("Klub 007");
        assertThat(reloaded.location().city()).isEqualTo("Hořice");
        assertThat(reloaded.location().country()).isEqualTo(Country.CZECHIA);
        assertThat(reloaded.lineup()).containsExactly("Eufory", "Support");
        assertThat(reloaded.admission().type().name()).isEqualTo("PAID");
        assertThat(reloaded.admission().amount()).isEqualTo("150 Kč");
        assertThat(reloaded.ticketUrl()).isEqualTo("https://tickets.example");
        assertThat(reloaded.cancelled()).isFalse();
    }

    @Test
    void keeps_a_festivals_length_and_the_bands_slot() {
        GigSchedule festival = new GigSchedule(prague(2026, 8, 27, 12, 0), prague(2026, 8, 30, 0, 0),
                new Slot(prague(2026, 8, 28, 19, 30), prague(2026, 8, 28, 20, 45)));
        Gig gig = new Gig("Moto Fest", festival, new Location("Camp", "Stará Turá", Country.CZECHIA), List.of(),
                Admission.free(), null, null, null, null, false);

        repository.save(gig);

        assertThat(repository.findById(gig.id())).get().extracting(Gig::schedule).isEqualTo(festival);
        repository.save(new Gig("Moto Fest", festival.withSlot(null), gig.location(), List.of(), Admission.free(),
                null, null, null, null, false));
        assertThat(repository.findById(gig.id()).orElseThrow().schedule().hasSlot()).as("slot removed").isFalse();
    }

    @Test
    void keeps_the_address_that_tells_same_named_towns_apart() {
        Address address = new Address("Pernštejnské nám. 1", "753 01", "okres Přerov", "Olomoucký kraj", 49.548, 17.735);
        Gig gig = new Gig("Fest", GigSchedule.startingAt(prague(2026, 9, 18, 21, 30)),
                new Location("Zámecký klub", "Hranice", Country.CZECHIA, address), List.of(), Admission.free(),
                null, null, null, null, false);

        repository.save(gig);

        assertThat(repository.findById(gig.id()).orElseThrow().location()).isEqualTo(gig.location());
        assertThat(repository.findById(fullGig().id())).isEmpty();
    }

    @Test
    void save_upserts_by_identity_same_date_and_venue() {
        Gig original = fullGig();
        repository.save(original);
        // same id (date + venue), different title/admission
        Gig edited = Gig.create("Renamed Fest", original.schedule(), original.location(),
                original.lineup(), Admission.free(), null, null, null, null);

        repository.save(edited);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(original.id())).get()
                .extracting(Gig::title).isEqualTo("Renamed Fest");
    }

    @Test
    void finds_gigs_starting_within_a_date_range_inclusive() {
        repository.save(fullGig()); // starts 2026-09-15

        assertThat(repository.findStartingWithin(
                new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))).hasSize(1);
        assertThat(repository.findStartingWithin(
                new DateRange(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15)))).hasSize(1); // boundary
        assertThat(repository.findStartingWithin(
                new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))).isEmpty();
    }

    @Test
    void deletes_by_identity() {
        Gig gig = fullGig();
        repository.save(gig);

        repository.deleteById(gig.id());

        assertThat(repository.findById(gig.id())).isEmpty();
    }
}
