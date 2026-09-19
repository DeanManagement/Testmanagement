-- PRD-026: Azure DevOps. Every request names an api-version and Azure DevOps Server 2019/2020 stop at
-- 5.0/6.0, so the connection carries one (null = the adapter's default, 7.1).
ALTER TABLE build_server_configs ADD COLUMN api_version VARCHAR(10);
ALTER TABLE issue_tracker_configs ADD COLUMN api_version VARCHAR(10);
-- The work item type bugs are filed as (null = Bug); a process may disable or rename it.
ALTER TABLE issue_tracker_configs ADD COLUMN work_item_type VARCHAR(100);

-- Pull the results Azure DevOps collected once a triggered run finishes, and remember that it was
-- tried, so a run whose pipeline published nothing is not asked again on every poll.
ALTER TABLE build_workflows ADD COLUMN pull_test_results BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE pipeline_runs ADD COLUMN results_pulled_at TIMESTAMP;
