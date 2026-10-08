package sk.drabikp.bzscraper.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sk.drabikp.bzscraper.domain.model.Address;
import sk.drabikp.bzscraper.domain.model.Country;
import sk.drabikp.bzscraper.domain.model.EntryType;

import java.time.LocalDateTime;

/**
 * JPA persistence model for a gig — this is the ONLY place JPA annotations live.
 * It is a flattened mirror of the {@link sk.drabikp.bzscraper.domain.model.Gig}
 * aggregate; {@link GigEntityMapper} translates between the two. The domain never
 * imports this class.
 *
 * Schedule is stored as wall-clock {@code LocalDateTime} plus the country; the
 * zone is reconstructed from the country on the way out, sidestepping Hibernate's
 * {@code ZonedDateTime} timezone handling. {@code id} is the serialized natural key
 * (start date + normalized venue), so {@code save} upserts by gig identity.
 */
@Entity
@Table(name = "gig")
public class GigEntity {

    @Id
    private String id;
    /** Optimistic lock: bumped on every update; a save based on an older one fails. */
    @Version
    private Long version;
    private String title;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    /** The band's own slot within the event (null when not given). */
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private String venue;
    private String city;
    /** The town's district etc. when it was picked from the place search (all may be null). */
    private String street;
    private String postalCode;
    private String district;
    private String region;
    private Double latitude;
    private Double longitude;
    @Enumerated(EnumType.STRING)
    private Country country;
    @Column(length = 2000)
    private String lineup;
    @Enumerated(EnumType.STRING)
    private EntryType entryType;
    private String entryFee;
    @Column(length = 4000)
    private String description;
    private String facebookUrl;
    private String ticketUrl;
    private String posterImageUrl;
    private boolean cancelled;

    protected GigEntity() {
        // for JPA
    }

    public GigEntity(String id, String title, LocalDateTime startDateTime, LocalDateTime endDateTime,
                     String venue, String city, Country country, String lineup, EntryType entryType,
                     String entryFee, String description, String facebookUrl, String ticketUrl,
                     String posterImageUrl, boolean cancelled) {
        this.id = id;
        this.title = title;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.venue = venue;
        this.city = city;
        this.country = country;
        this.lineup = lineup;
        this.entryType = entryType;
        this.entryFee = entryFee;
        this.description = description;
        this.facebookUrl = facebookUrl;
        this.ticketUrl = ticketUrl;
        this.posterImageUrl = posterImageUrl;
        this.cancelled = cancelled;
    }

    void setSlot(LocalDateTime start, LocalDateTime end) {
        this.slotStart = start;
        this.slotEnd = end;
    }

    void setAddress(Address address) {
        this.street = address == null ? null : address.street();
        this.postalCode = address == null ? null : address.postalCode();
        this.district = address == null ? null : address.district();
        this.region = address == null ? null : address.region();
        this.latitude = address == null ? null : address.latitude();
        this.longitude = address == null ? null : address.longitude();
    }

    Address getAddress() {
        Address address = new Address(street, postalCode, district, region, latitude, longitude);
        return address.equals(new Address(null, null, null, null, null, null)) ? null : address;
    }

    LocalDateTime getSlotStart() {
        return slotStart;
    }

    LocalDateTime getSlotEnd() {
        return slotEnd;
    }

    Long getVersion() {
        return version;
    }

    /** Carries over the version the row had when it was read in this transaction. */
    void setVersion(Long version) {
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public String getVenue() {
        return venue;
    }

    public String getCity() {
        return city;
    }

    public Country getCountry() {
        return country;
    }

    public String getLineup() {
        return lineup;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public String getEntryFee() {
        return entryFee;
    }

    public String getDescription() {
        return description;
    }

    public String getFacebookUrl() {
        return facebookUrl;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public String getPosterImageUrl() {
        return posterImageUrl;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
