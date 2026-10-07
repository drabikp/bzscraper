-- The gig catalog, exactly as Hibernate generated it before Flyway was introduced.
-- Databases created that way are baselined at version 1 (spring.flyway.baseline-*),
-- so this script only runs on a fresh database.
create table gig (
    id               varchar(255) not null,
    title            varchar(255),
    start_date_time  timestamp(6),
    end_date_time    timestamp(6),
    venue            varchar(255),
    city             varchar(255),
    country          enum ('CZECHIA','SLOVAKIA'),
    lineup           varchar(2000),
    entry_type       enum ('FREE','PAID','VOLUNTARY'),
    entry_fee        varchar(255),
    description      varchar(4000),
    facebook_url     varchar(255),
    ticket_url       varchar(255),
    poster_image_url varchar(255),
    cancelled        boolean not null,
    primary key (id)
);
