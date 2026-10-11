CREATE TABLE savings_goal (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    currency CHAR(3) NOT NULL,
    target_minor BIGINT NOT NULL CHECK (target_minor > 0),
    saved_minor BIGINT NOT NULL DEFAULT 0 CHECK (saved_minor >= 0 AND saved_minor <= target_minor),
    target_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (currency = upper(currency)),
    CONSTRAINT uq_savings_goal_id_user UNIQUE (id, user_id)
);
CREATE INDEX idx_savings_goal_user ON savings_goal(user_id, created_at DESC);

CREATE TABLE recurring_rule (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    wallet_id BIGINT NOT NULL,
    type VARCHAR(12) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    category VARCHAR(80) NOT NULL,
    description VARCHAR(240) NOT NULL DEFAULT '',
    frequency VARCHAR(12) NOT NULL CHECK (frequency IN ('WEEKLY','MONTHLY','YEARLY')),
    next_run_at DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_recurring_wallet_owner FOREIGN KEY (wallet_id, user_id) REFERENCES wallet(id, user_id) ON DELETE CASCADE,
    CONSTRAINT uq_recurring_rule_id_user UNIQUE (id, user_id)
);
CREATE INDEX idx_recurring_due ON recurring_rule(active, next_run_at) WHERE active = TRUE;
CREATE INDEX idx_recurring_user ON recurring_rule(user_id, id);

ALTER TABLE finance_transaction ADD CONSTRAINT uq_transaction_id_user UNIQUE (id, user_id);

CREATE TABLE recurring_execution (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    rule_id BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    transaction_id BIGINT NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(rule_id, scheduled_date),
    CONSTRAINT fk_execution_rule_owner FOREIGN KEY (rule_id, user_id) REFERENCES recurring_rule(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_execution_transaction_owner FOREIGN KEY (transaction_id, user_id) REFERENCES finance_transaction(id, user_id) ON DELETE RESTRICT
);
CREATE INDEX idx_recurring_execution_user ON recurring_execution(user_id, executed_at DESC);

CREATE TABLE savings_goal_contribution (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    goal_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    note VARCHAR(240) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_goal_contribution_wallet_owner FOREIGN KEY (wallet_id, user_id) REFERENCES wallet(id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_goal_contribution_goal_owner FOREIGN KEY (goal_id, user_id) REFERENCES savings_goal(id, user_id) ON DELETE CASCADE
);
CREATE INDEX idx_goal_contribution_goal ON savings_goal_contribution(user_id, goal_id, created_at DESC);
