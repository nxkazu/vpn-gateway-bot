-- Plans move from a hard-coded catalogue into the database (editable via the admin dashboard).
CREATE TABLE plans (
    code        TEXT        PRIMARY KEY,
    title       TEXT        NOT NULL,
    days        INT         NOT NULL,
    traffic_gb  BIGINT      NOT NULL,
    price_rub   BIGINT      NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO plans (code, title, days, traffic_gb, price_rub, sort_order) VALUES
    ('m1',  '1 месяц / 1 month',    30,  100,  149, 1),
    ('m3',  '3 месяца / 3 months',  90,  300,  399, 2),
    ('m6',  '6 месяцев / 6 months', 180, 600,  749, 3),
    ('m12', '1 год / 1 year',       365, 1200, 1290, 4);

-- Support tickets: a user opens a ticket in the bot, admins reply from the dashboard.
CREATE TABLE support_tickets (
    id          BIGSERIAL   PRIMARY KEY,
    telegram_id BIGINT      NOT NULL REFERENCES bot_users(telegram_id) ON DELETE CASCADE,
    status      TEXT        NOT NULL DEFAULT 'OPEN',
    subject     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tickets_status ON support_tickets(status, updated_at DESC);
CREATE INDEX idx_tickets_user ON support_tickets(telegram_id);

CREATE TABLE support_messages (
    id          BIGSERIAL   PRIMARY KEY,
    ticket_id   BIGINT      NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender      TEXT        NOT NULL, -- USER | ADMIN
    sender_name TEXT,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_messages ON support_messages(ticket_id, id);