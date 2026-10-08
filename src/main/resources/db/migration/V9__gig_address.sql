-- Where exactly a gig is, beyond the town's name: what tells same-named towns apart for the
-- platforms (district, region, postal code, the town's coordinates). All optional.
alter table gig add column street varchar(255);
alter table gig add column postal_code varchar(20);
alter table gig add column district varchar(100);
alter table gig add column region varchar(100);
alter table gig add column latitude double precision;
alter table gig add column longitude double precision;
