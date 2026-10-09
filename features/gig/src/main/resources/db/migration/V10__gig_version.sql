-- Optimistic locking: every save of a gig bumps its version, so two saves of the same gig at
-- the same time can't both win (the later one fails instead of silently overwriting).
alter table gig add column version bigint default 0 not null;
