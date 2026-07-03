-- Promo codes (managed from the admin dashboard).
CREATE TABLE promo_codes (
    code             TEXT        PRIMARY KEY,
    discount_percent INT         NOT NULL CHECK (discount_percent > 0 AND discount_percent < 100),
    max_uses         INT         NOT NULL DEFAULT 0, -- 0 = unlimited
    uses             INT         NOT NULL DEFAULT 0,
    expires_at       TIMESTAMPTZ,
    enabled          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One redemption per (code, user); written when the discounted invoice is paid.
CREATE TABLE promo_redemptions (
    id          BIGSERIAL   PRIMARY KEY,
    code        TEXT        NOT NULL REFERENCES promo_codes(code) ON DELETE CASCADE,
    telegram_id BIGINT      NOT NULL REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    invoice_id  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, telegram_id)
);

-- Invoices remember the promo code that discounted them.
ALTER TABLE payment_invoices ADD COLUMN promo_code TEXT;

-- Support messages can carry a Telegram attachment (photo / document).
ALTER TABLE support_messages ADD COLUMN attachment_file_id TEXT;
ALTER TABLE support_messages ADD COLUMN attachment_name TEXT;

-- One "your subscription is expiring" notice per (user, expiry date).
CREATE TABLE expiry_notices (
    telegram_id BIGINT      NOT NULL,
    expire_date DATE        NOT NULL,
    notified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (telegram_id, expire_date)
);