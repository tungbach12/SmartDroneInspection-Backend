ALTER TABLE service_requests
    ADD COLUMN assigned_manager_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN manager_assigned_at TIMESTAMPTZ,
    ADD COLUMN manager_assigned_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT;

CREATE INDEX ix_service_requests_assigned_manager
    ON service_requests (assigned_manager_id, created_at DESC)
    WHERE assigned_manager_id IS NOT NULL;
