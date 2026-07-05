-- Update 8: gift subscriptions.
-- A gift is a paid plan converted into a one-time redeemable code instead of an
-- immediate activation. Lifecycle: PENDING (invoice unpaid) -> ACTIVE (paid,
-- redeemable) -> REDEEMED.

ALTER TABLE payment_invoices ADD COLUMN kind TEXT NOT NULL DEFAULT 'PLAN';

CREATE TABLE gifts (
    id                BIGSERIAL   PRIMARY KEY,
    code              TEXT        NOT NULL UNIQUE,
    buyer_telegram_id BIGINT      NOT NULL REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    plan_code         TEXT        NOT NULL,
    invoice_id        BIGINT      REFERENCES payment_invoices(id) ON DELETE SET NULL,
    status            TEXT        NOT NULL DEFAULT 'PENDING',
    redeemed_by       BIGINT      REFERENCES bot_users(telegram_id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    redeemed_at       TIMESTAMPTZ
);

CREATE INDEX idx_gifts_buyer   ON gifts(buyer_telegram_id);
CREATE INDEX idx_gifts_invoice ON gifts(invoice_id);
