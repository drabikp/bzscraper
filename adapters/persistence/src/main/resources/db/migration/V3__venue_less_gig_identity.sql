-- A gig without a known venue is now identified by date + "@" + city (it was date + ""),
-- so two venue-less gigs on one day in different cities no longer share an identity.
-- Re-key such rows and their publication records (same normalisation as GigId: trim,
-- lowercase, collapse whitespace).
update published_gig p
   set gig_id = gig_id || '@' || (select regexp_replace(lower(trim(g.city)), '\s+', ' ') from gig g where g.id = p.gig_id)
 where p.gig_id like '%|'
   and exists (select 1 from gig g where g.id = p.gig_id);

update gig
   set id = id || '@' || regexp_replace(lower(trim(city)), '\s+', ' ')
 where id like '%|';
