-- The core knows no platform by name: a platform is an id an adapter contributes. The
-- records' platform column was an enum of the first two platforms — it becomes text like
-- sync_task.platform (both widened alike). Stored values do not change.
alter table published_gig alter column platform set data type varchar(40);
alter table sync_task alter column platform set data type varchar(40);
