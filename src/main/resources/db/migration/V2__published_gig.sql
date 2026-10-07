-- Which catalog gig is published on which platform, and under which platform id.
-- gig_id is the serialized GigId (same format as gig.id). No foreign key on purpose:
-- a record outlives its catalog row when deleting the gig on the platform failed, and
-- it still has to point at that platform copy.
create table published_gig (
    platform     enum ('BANDZONE','BANDSINTOWN') not null,
    gig_id       varchar(255) not null,
    external_ref varchar(255),
    primary key (platform, gig_id)
);
