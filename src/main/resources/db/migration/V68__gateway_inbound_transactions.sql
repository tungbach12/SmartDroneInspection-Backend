-- Audit trail of every authenticated inbound gateway notification (Sepay webhook) so transfers that
-- could not be applied (amount mismatch, unknown order, system error) can be reconciled manually and
-- a redelivered notification is recognised instead of being applied twice.
CREATE TABLE gateway_inbound_transactions (
    id uuid PRIMARY KEY,
    gateway character varying(20) NOT NULL,
    external_id character varying(100),
    order_code bigint,
    amount bigint,
    account_number character varying(50),
    payload text,
    status character varying(24) NOT NULL,
    detail character varying(255),
    received_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

CREATE UNIQUE INDEX ux_gateway_inbound_gateway_external
    ON gateway_inbound_transactions (gateway, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX ix_gateway_inbound_status ON gateway_inbound_transactions (status, received_at DESC);
