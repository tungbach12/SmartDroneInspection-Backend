-- V21: Align maintenance_orders status check with dispute_tickets contract.
--
-- Context:
--   V20 created dispute_tickets with order_type IN ('INSPECTION', 'MAINTENANCE'),
--   and its header comment asserts that opening a dispute pauses acceptance and
--   payment by moving the order into status DISPUTED.
--   V16 added DISPUTED to inspection_service_orders.status, but omitted DISPUTED
--   from ck_maintenance_orders_status (which admitted only CONFIRMED, IN_PROGRESS,
--   COMPLETED, AWAITING_PAYMENT, PAID, SUPERSEDED, CANCELLED).
--
-- Change:
--   Widen ck_maintenance_orders_status to include 'DISPUTED'.
--   No existing rows are modified. The constraint drop-and-re-add is forward-only
--   and idempotent.

ALTER TABLE maintenance_orders DROP CONSTRAINT IF EXISTS ck_maintenance_orders_status;
ALTER TABLE maintenance_orders
    ADD CONSTRAINT ck_maintenance_orders_status CHECK (status IN (
        'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'AWAITING_PAYMENT', 'PAID',
        'DISPUTED', 'SUPERSEDED', 'CANCELLED'
    ));

COMMENT ON CONSTRAINT ck_maintenance_orders_status ON maintenance_orders IS
    'Admissible maintenance order states. DISPUTED added in V21 to mirror inspection_service_orders when a maintenance dispute is raised.';
