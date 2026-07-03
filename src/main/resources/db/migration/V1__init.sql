-- Core bot user record. Mirrors just enough Telegram + Remnawave linkage
-- to drive the bot; the VPN account of record lives in the Remnawave panel.
CREATE TABLE bot_users (
    telegram_id      BIGINT       PRIMARY KEY,
    username         TEXT,
    first_name       TEXT,
    language_code    TEXT,
    remnawave_uuid   UUID,
    remnawave_username TEXT,
    is_blocked       BOOLEAN      NOT NULL DEFAULT FALSE,
    trial_used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Referral graph: one row per referred user, pointing at their referrer.
CREATE TABLE referrals (
    referred_id      BIGINT       PRIMARY KEY REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    referrer_id      BIGINT       NOT NULL REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rewarded         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT no_self_referral CHECK (referred_id <> referrer_id)
);
CREATE INDEX idx_referrals_referrer ON referrals(referrer_id);

-- Ledger of rewards granted to referrers (audit trail + idempotency).
CREATE TABLE referral_rewards (
    id               BIGSERIAL    PRIMARY KEY,
    referrer_id      BIGINT       NOT NULL REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    referred_id      BIGINT       NOT NULL REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    reward_days      INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (referrer_id, referred_id)
);
CREATE INDEX idx_rewards_referrer ON referral_rewards(referrer_id);

-- Payment / plan activation records (extend this as billing providers are added).
CREATE TABLE payments (
    id               BIGSERIAL    PRIMARY KEY,
    telegram_id      BIGINT       NOT NULL REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    plan_code        TEXT         NOT NULL,
    amount_cents     BIGINT       NOT NULL DEFAULT 0,
    currency         TEXT         NOT NULL DEFAULT 'RUB',
    days             INT          NOT NULL,
    status           TEXT         NOT NULL DEFAULT 'PAID',
    provider         TEXT,
    provider_ref     TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_user ON payments(telegram_id);
