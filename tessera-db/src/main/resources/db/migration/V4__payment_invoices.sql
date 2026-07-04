-- Locally tracked payment invoices (one row per provider transaction).
-- The provider-side status is the source of truth; this table drives the bot UX
-- and gives idempotency for plan activation.
CREATE TABLE payment_invoices (
    id               BIGSERIAL    PRIMARY KEY,
    telegram_id      BIGINT       NOT NULL REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    plan_code        TEXT         NOT NULL,
    provider         TEXT         NOT NULL,
    external_id      TEXT         NOT NULL,
    pay_url          TEXT,
    amount_cents     BIGINT       NOT NULL,
    currency         TEXT         NOT NULL DEFAULT 'RUB',
    status           TEXT         NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoices_user ON payment_invoices(telegram_id);
CREATE UNIQUE INDEX uq_invoices_provider_ext ON payment_invoices(provider, external_id);