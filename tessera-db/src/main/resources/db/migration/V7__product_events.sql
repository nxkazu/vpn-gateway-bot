-- Product analytics events for Tessera Pulse.
-- Events are intentionally append-only: they power funnel metrics and UX diagnostics
-- without changing the core payment/subscription state.
CREATE TABLE user_events (
    id          BIGSERIAL   PRIMARY KEY,
    telegram_id BIGINT      REFERENCES bot_users(telegram_id) ON DELETE SET NULL,
    event_type  TEXT        NOT NULL,
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_events_type_created ON user_events(event_type, created_at DESC);
CREATE INDEX idx_user_events_user_created ON user_events(telegram_id, created_at DESC);
