-- V7: Supplementary alerting indexes and refinements
-- Alert tables are created in V4 (alerting schema). This migration
-- adds any additional indexes or refinements needed.

SET search_path TO alerting;

-- Additional composite index for unresolved alerts
CREATE INDEX IF NOT EXISTS idx_alert_instances_unresolved
    ON alert_instances(tenant_id, status, severity)
    WHERE status <> 'RESOLVED';
