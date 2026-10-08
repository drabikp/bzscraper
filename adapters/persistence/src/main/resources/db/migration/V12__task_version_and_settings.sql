-- Optimistic lock on sync tasks: the worker and a user (Retry, Discard, a delete superseding
-- queued work) writing one task at the same time can't both win — the later write fails
-- instead of silently overwriting the other.
alter table sync_task add column version bigint default 0 not null;

-- Small app settings that must survive a restart (e.g. the sync paused by the user).
create table app_setting (
    name  varchar(100) not null primary key,
    setting_value varchar(1000)
);
