-- Production authentication foundation.
-- Secrets and raw tokens are never persisted; only adaptive password hashes,
-- keyed token hashes and audit metadata are stored.

CREATE TABLE IF NOT EXISTS organizations
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(200) NOT NULL,
    code           VARCHAR(64) NOT NULL,
    description    VARCHAR(2000),
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_organizations_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS users
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(320) NOT NULL,
    normalized_email    VARCHAR(320) NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    password_hash       VARCHAR(1024),
    status              VARCHAR(32) NOT NULL,
    actor_zone          VARCHAR(32) NOT NULL,
    organization_id     UUID REFERENCES organizations(id) ON DELETE SET NULL,
    auth_version        INTEGER NOT NULL DEFAULT 0,
    failed_login_count  INTEGER NOT NULL DEFAULT 0,
    lockout_until       TIMESTAMPTZ,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at       TIMESTAMPTZ,
    last_login_ip       VARCHAR(45),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_users_actor_zone CHECK (actor_zone IN ('PLATFORM', 'CUSTOMER_ORGANIZATION', 'SERVICE_WORKFORCE')),
    CONSTRAINT ck_users_zone_organization CHECK (
        (actor_zone = 'CUSTOMER_ORGANIZATION' AND organization_id IS NOT NULL)
        OR (actor_zone <> 'CUSTOMER_ORGANIZATION' AND organization_id IS NULL)
    ),
    CONSTRAINT ck_users_failed_login_count CHECK (failed_login_count >= 0)
);

CREATE INDEX IF NOT EXISTS ix_users_organization ON users(organization_id);
CREATE INDEX IF NOT EXISTS ix_users_status ON users(status);

CREATE TABLE IF NOT EXISTS user_roles
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role             VARCHAR(64) NOT NULL,
    CONSTRAINT uq_user_roles UNIQUE (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN (
        'PLATFORM_ADMINISTRATOR', 'ORGANIZATION_MANAGER',
        'SERVICE_OPERATIONS_MANAGER', 'INSPECTOR', 'MAINTENANCE_ENGINEER'
    ))
);

CREATE INDEX IF NOT EXISTS ix_user_roles_user ON user_roles(user_id);

CREATE TABLE IF NOT EXISTS auth_sessions
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_type     VARCHAR(16) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoked_reason  VARCHAR(128),
    CONSTRAINT ck_auth_sessions_client CHECK (client_type IN ('WEB', 'MOBILE'))
);

CREATE INDEX IF NOT EXISTS ix_auth_sessions_user ON auth_sessions(user_id);
CREATE INDEX IF NOT EXISTS ix_auth_sessions_active ON auth_sessions(user_id, revoked_at);

CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id           UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    token_hash           VARCHAR(64) NOT NULL,
    issued_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at           TIMESTAMPTZ NOT NULL,
    revoked_at           TIMESTAMPTZ,
    revoke_reason        VARCHAR(128),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_session ON refresh_tokens(session_id);

CREATE TABLE IF NOT EXISTS security_audit_events
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    subject_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type     VARCHAR(96) NOT NULL,
    outcome        VARCHAR(16) NOT NULL,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(1000),
    correlation_id VARCHAR(128),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_security_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX IF NOT EXISTS ix_security_audit_event_time ON security_audit_events(event_type, occurred_at);
CREATE INDEX IF NOT EXISTS ix_security_audit_subject_time ON security_audit_events(subject_user_id, occurred_at);
