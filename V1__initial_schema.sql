CREATE TABLE app_user (
 id BIGSERIAL PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE wallet (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 name VARCHAR(80) NOT NULL,
 currency CHAR(3) NOT NULL DEFAULT 'EUR',
 balance_minor BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT wallet_currency_upper CHECK (currency = upper(currency)),
 CONSTRAINT wallet_balance_nonnegative CHECK (balance_minor >= 0)
);
CREATE INDEX idx_wallet_user ON wallet(user_id);
CREATE TABLE finance_transaction (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 wallet_id BIGINT NOT NULL REFERENCES wallet(id),
 type VARCHAR(12) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
 amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
 category VARCHAR(80) NOT NULL,
 description VARCHAR(240) NOT NULL DEFAULT '',
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tx_user_date ON finance_transaction(user_id, occurred_at DESC);
CREATE TABLE wallet_transfer (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 from_wallet_id BIGINT NOT NULL REFERENCES wallet(id),
 to_wallet_id BIGINT NOT NULL REFERENCES wallet(id),
 amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
 description VARCHAR(240) NOT NULL DEFAULT '',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK (from_wallet_id <> to_wallet_id)
);
CREATE TABLE budget (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 name VARCHAR(80) NOT NULL,
 category VARCHAR(80) NOT NULL,
 limit_minor BIGINT NOT NULL CHECK (limit_minor > 0),
 month CHAR(7) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(user_id, category, month)
);
CREATE INDEX idx_budget_user_month ON budget(user_id, month);
