-- The partner Inspector and drone are no longer chosen while preparing a quote.
-- Assignment happens at dispatch (step 4) and is confirmed by the Inspector, so a quote may
-- legitimately carry no partner pair. Existing rows keep the pair they were issued with.
ALTER TABLE service_request_quotes
    ALTER COLUMN inspector_id DROP NOT NULL,
    ALTER COLUMN drone_id DROP NOT NULL;

COMMENT ON COLUMN service_request_quotes.inspector_id IS
    'Legacy partner Inspector recorded when quotes still carried the assignment; assignment now happens at dispatch.';
COMMENT ON COLUMN service_request_quotes.drone_id IS
    'Legacy partner drone recorded when quotes still carried the assignment; assignment now happens at dispatch.';
