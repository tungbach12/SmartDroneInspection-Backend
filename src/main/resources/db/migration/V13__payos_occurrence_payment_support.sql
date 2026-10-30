-- Migration V13: PayOS occurrence payment support
-- Adds orderCode sequence for unique PayOS 64-bit transaction references,
-- and tracks PayOS checkout link id and payment method on service_request_occurrences.

CREATE SEQUENCE service_request_occurrences_payos_order_code_seq START WITH 100001 INCREMENT BY 1;

ALTER TABLE service_request_occurrences
    ADD COLUMN payos_order_code bigint UNIQUE,
    ADD COLUMN payos_payment_link_id character varying(100),
    ADD COLUMN payment_method character varying(30);
