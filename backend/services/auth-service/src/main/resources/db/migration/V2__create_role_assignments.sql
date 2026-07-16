-- Role assignments — append-only role history, per docs/services/auth-service/database-design.md
-- ("Role Assignment ... modeled as its own concept rather than a column on Credential so that
-- role history/audit is a natural extension"). Rows are immutable facts: a role change is a new
-- row, never an UPDATE, and a user's current role is their most recent row.

CREATE TABLE role_assignments
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    assigned_by VARCHAR(255) NOT NULL,
    assigned_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_role_assignments_user FOREIGN KEY (user_id) REFERENCES credentials (id),
    CONSTRAINT chk_role_assignments_role CHECK (role IN ('TRAVELER', 'OPERATOR', 'ADMIN', 'SUPPORT'))
);

-- The current-role lookup ("latest assignment for this user") runs on every login and every
-- token refresh — the composite index serves it without scanning a user's full role history.
CREATE INDEX idx_role_assignments_user_latest ON role_assignments (user_id, assigned_at DESC);
