-- MF3-06/MF3-08: the Inspector corrects the findings record while the report is not submitted.
-- A deleted finding keeps who removed it, when and why, so the inspection history stays auditable;
-- every read of verified_findings filters deleted rows out.
ALTER TABLE verified_findings
    ADD COLUMN deleted_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deletion_reason VARCHAR(2000);

-- MF3-02: an original capture is never overwritten or removed. Retiring one takes it out of the
-- current evidence set used for drafting and verification while its bytes, checksum and metadata
-- remain available for audit.
ALTER TABLE evidence
    ADD COLUMN retired_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN retired_at TIMESTAMPTZ,
    ADD COLUMN retirement_reason VARCHAR(2000);

-- MF3-08: an author may withdraw a submission before the reviewer acted on it. The version keeps its
-- number and content - a submission is a state, not a new document - so the withdrawal is recorded
-- on the version rather than by creating another one.
ALTER TABLE inspection_report_versions
    ADD COLUMN withdrawn_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN withdrawn_at TIMESTAMPTZ,
    ADD COLUMN withdrawal_reason VARCHAR(2000);

-- MF3-09: a reviewer return says what kind of revision it asks for. TEXT_REVISION keeps the findings
-- baseline and only asks for report changes; FINDINGS_REVISION reopens Findings, which invalidates
-- the confirmation and requires the Inspector to re-confirm before drafting again.
ALTER TABLE inspection_report_versions
    ADD COLUMN return_reason_type VARCHAR(32);
