-- Where a workflow run stands: the step it waits for or runs (null: not started, or an
-- action without a workflow yet). See docs/sync-workflow-plan.md.
alter table sync_task add column step varchar(40);
