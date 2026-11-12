-- Keep one editable quote per request. Historical quote revisions are archived in audit_events
-- before removing the user-visible version relationship.
ALTER TABLE service_requests
    ADD COLUMN accepted_quote_id UUID,
    ADD COLUMN accepted_at TIMESTAMPTZ,
    ADD COLUMN accepted_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN accepted_terms_snapshot JSONB;

ALTER TABLE service_request_quotes
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN updated_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

UPDATE service_request_quotes
SET updated_at = created_at,
    updated_by_user_id = issued_by_user_id;

ALTER TABLE service_request_quotes
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_by_user_id SET NOT NULL;

UPDATE service_requests request
SET accepted_quote_id = quote.id,
    accepted_at = COALESCE(request.contract_uploaded_at, request.updated_at),
    accepted_terms_snapshot = jsonb_build_object(
        'quoteId', quote.id,
        'scopeSnapshot', quote.scope_snapshot,
        'firstOccurrenceStartAt', quote.first_occurrence_start_at,
        'firstOccurrenceEndAt', quote.first_occurrence_end_at,
        'inspectorId', quote.inspector_id,
        'droneId', quote.drone_id,
        'pricePerOccurrenceVnd', quote.price_per_occurrence_vnd,
        'issuedByUserId', quote.issued_by_user_id,
        'acceptedAt', COALESCE(request.contract_uploaded_at, request.updated_at),
        'legacyQuoteVersion', quote.version_no
    )
FROM service_request_quotes quote
WHERE quote.request_id = request.id
  AND quote.version_no = request.accepted_quote_version;

INSERT INTO audit_events (
    organization_id, actor_user_id, action, aggregate_type, aggregate_id,
    before_status, after_status, aggregate_version, safe_metadata, created_at
)
SELECT request.organization_id,
       NULL,
       'MF2_LEGACY_QUOTE_ARCHIVED',
       'SERVICE_REQUEST',
       quote.request_id,
       request.status,
       request.status,
       quote.version_no,
       jsonb_build_object(
           'quoteId', quote.id,
           'legacyQuoteVersion', quote.version_no,
           'scopeSnapshot', quote.scope_snapshot,
           'firstOccurrenceStartAt', quote.first_occurrence_start_at,
           'firstOccurrenceEndAt', quote.first_occurrence_end_at,
           'inspectorId', quote.inspector_id,
           'droneId', quote.drone_id,
           'pricePerOccurrenceVnd', quote.price_per_occurrence_vnd,
           'issuedByUserId', quote.issued_by_user_id,
           'createdAt', quote.created_at
       ),
       quote.created_at
FROM service_request_quotes quote
JOIN service_requests request ON request.id = quote.request_id
WHERE (request.accepted_quote_version IS NOT NULL
       AND quote.version_no <> request.accepted_quote_version)
   OR (request.accepted_quote_version IS NULL
       AND quote.version_no < (SELECT MAX(latest.version_no)
                               FROM service_request_quotes latest
                               WHERE latest.request_id = quote.request_id));

DELETE FROM service_request_quotes quote
USING service_requests request
WHERE quote.request_id = request.id
  AND ((request.accepted_quote_version IS NOT NULL
        AND quote.version_no <> request.accepted_quote_version)
       OR (request.accepted_quote_version IS NULL
           AND quote.version_no < (SELECT MAX(latest.version_no)
                                   FROM service_request_quotes latest
                                   WHERE latest.request_id = quote.request_id)));

ALTER TABLE service_requests
    DROP CONSTRAINT IF EXISTS fk_service_requests_accepted_quote,
    DROP CONSTRAINT IF EXISTS ck_service_requests_acceptance,
    DROP CONSTRAINT IF EXISTS fk_service_requests_acceptor,
    DROP COLUMN accepted_quote_version,
    ADD CONSTRAINT fk_service_requests_accepted_quote_id
        FOREIGN KEY (accepted_quote_id, id)
        REFERENCES service_request_quotes(id, request_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_service_requests_acceptance_v33 CHECK (
        (status IN ('OPEN', 'QUOTED', 'CANCELLED')
            AND accepted_quote_id IS NULL
            AND accepted_at IS NULL
            AND accepted_terms_snapshot IS NULL)
        OR
        (status IN ('ACCEPTED', 'CONTRACT_SIGNED', 'ASSIGNED')
            AND accepted_quote_id IS NOT NULL
            AND accepted_at IS NOT NULL
            AND accepted_terms_snapshot IS NOT NULL)
    );

DROP INDEX IF EXISTS ix_service_request_quotes_latest;

ALTER TABLE service_request_quotes
    DROP CONSTRAINT IF EXISTS uq_service_request_quotes_version,
    DROP CONSTRAINT IF EXISTS ck_service_request_quotes_version,
    DROP COLUMN version_no,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT uq_service_request_quotes_request UNIQUE (request_id);

CREATE OR REPLACE FUNCTION prevent_accepted_service_quote_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM service_requests request
        WHERE request.accepted_quote_id = OLD.id
    ) THEN
        RAISE EXCEPTION 'Accepted service quote terms are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_service_request_quote_accepted_immutable
BEFORE UPDATE OR DELETE ON service_request_quotes
FOR EACH ROW EXECUTE FUNCTION prevent_accepted_service_quote_mutation();
