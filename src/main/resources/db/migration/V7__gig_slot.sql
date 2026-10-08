-- The band's own slot within a longer event (a festival over several days): when the band
-- plays. start/end_date_time stay the whole event. Both null when no slot is given.
alter table gig add column slot_start timestamp(6);
alter table gig add column slot_end timestamp(6);
