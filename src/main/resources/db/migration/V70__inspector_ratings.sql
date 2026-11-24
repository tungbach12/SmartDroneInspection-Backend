CREATE TABLE inspector_ratings (
    id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    inspection_id uuid NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    inspector_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    rater_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    rater_role varchar(16) NOT NULL,
    overall_score integer NOT NULL,
    criterion_scores jsonb NOT NULL DEFAULT '{}'::jsonb,
    comment text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_inspector_ratings_inspection_role UNIQUE (inspection_id, rater_role),
    CONSTRAINT ck_inspector_ratings_rater_role CHECK (rater_role IN ('CUSTOMER', 'MANAGER')),
    CONSTRAINT ck_inspector_ratings_overall_score CHECK (overall_score BETWEEN 1 AND 5)
);

CREATE INDEX ix_inspector_ratings_inspector_created
    ON inspector_ratings (inspector_id, created_at DESC);

CREATE INDEX ix_inspector_ratings_inspection
    ON inspector_ratings (inspection_id);
