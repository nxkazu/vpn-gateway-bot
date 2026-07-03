-- Audit trail of every administrative action taken through the bot's admin panel.
CREATE TABLE admin_actions (
    id            BIGSERIAL    PRIMARY KEY,
    admin_id      BIGINT       NOT NULL,
    action        TEXT         NOT NULL,
    target        TEXT,
    details       TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_actions_created ON admin_actions(created_at DESC);
CREATE INDEX idx_admin_actions_admin ON admin_actions(admin_id);
