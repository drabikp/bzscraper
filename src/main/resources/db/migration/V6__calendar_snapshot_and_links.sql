-- The calendar as last read: each read is compared with it to find new, changed and
-- removed events. change_* is what the user hasn't seen yet (null when nothing).
-- notes are the band's private notes (fees, phones): shown in the app only, never published.
create table calendar_event (
    event_uid        varchar(512)  not null primary key,
    title            varchar(2000) not null,
    location         varchar(2000) not null,
    notes            varchar(1000000) not null,
    starts_at        timestamp(6)  not null,
    ends_at          timestamp(6)  not null,
    all_day          boolean       not null,
    repeating        boolean       not null,
    shown_as_free    boolean       not null,
    calendar_status  varchar(20)   not null,
    last_modified    timestamp(6) with time zone,
    suggested        varchar(20)   not null,
    first_seen       timestamp(6) with time zone not null,
    last_seen        timestamp(6) with time zone not null,
    removed_at       timestamp(6) with time zone,
    change_type      varchar(20),
    change_fields    varchar(200),
    suggested_before varchar(20),
    changed_at       timestamp(6) with time zone
);

-- Which catalog gig a calendar event is (gig_id = serialized GigId, re-keyed when an edit
-- changes the gig's identity). No foreign key: a deleted gig leaves a link that matches nothing.
create table calendar_link (
    event_uid varchar(512) not null primary key,
    gig_id    varchar(255) not null,
    linked_at timestamp(6) with time zone not null
);
create index calendar_link_gig on calendar_link (gig_id);
