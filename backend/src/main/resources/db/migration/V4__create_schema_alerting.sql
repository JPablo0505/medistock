-- V4__create_schema_alerting.sql
-- Alerting bounded context schema.
-- Owned by: AlertingContext

CREATE SCHEMA IF NOT EXISTS alerting;
SET search_path TO alerting;

-- Alert rules — define thresholds and conditions for alerts
CREATE TABLE alert_rules (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    alert_type      VARCHAR(30)  NOT NULL
                    CHECK (alert_type IN ('LOW_STOCK', 'NEAR_EXPIRY', 'EXPIRED', 'BATCH_QUARANTINED')),
    severity        VARCHAR(10)  NOT NULL DEFAULT 'WARNING'
                    CHECK (severity IN ('WARNING', 'CRITICAL')),
    condition_config JSONB       NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_alert_rules_tenant ON alert_rules(tenant_id);
CREATE INDEX idx_alert_rules_type ON alert_rules(tenant_id, alert_type);

-- Alert instances — individual triggered alerts
CREATE TABLE alert_instances (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36)  NOT NULL,
    alert_rule_id   VARCHAR(36)  REFERENCES alert_rules(id),
    alert_type      VARCHAR(30)  NOT NULL,
    severity        VARCHAR(10)  NOT NULL,
    title           VARCHAR(300) NOT NULL,
    message         TEXT,
    reference_type  VARCHAR(30),          -- 'BATCH', 'PRODUCT', etc.
    reference_id    VARCHAR(36),
    is_dismissed    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_resolved     BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_alert_instances_tenant ON alert_instances(tenant_id);
CREATE INDEX idx_alert_instances_type ON alert_instances(tenant_id, alert_type, is_resolved);
CREATE INDEX idx_alert_instances_unresolved ON alert_instances(tenant_id, is_resolved, severity);
