-- V7: Create alert tables for the alerting bounded context
-- Depends on: V1 (shared schema), V2 (product catalog), V4 (inventory batches)

CREATE TABLE alert_rules (
    id UUID NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(50) NOT NULL,
    config_json TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE alert_instances (
    id UUID NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    alert_rule_id UUID NOT NULL,
    product_id UUID NOT NULL,
    batch_id UUID,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_alert_instances_status ON alert_instances(status);
CREATE INDEX idx_alert_instances_rule_product ON alert_instances(alert_rule_id, product_id);
CREATE INDEX idx_alert_rules_type ON alert_rules(rule_type);
