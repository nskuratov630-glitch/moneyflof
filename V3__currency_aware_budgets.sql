ALTER TABLE budget ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'EUR';
ALTER TABLE budget DROP CONSTRAINT budget_user_id_category_month_key;
ALTER TABLE budget ADD CONSTRAINT uq_budget_user_category_month_currency UNIQUE (user_id, category, month, currency);
ALTER TABLE budget ADD CONSTRAINT budget_currency_upper CHECK (currency = upper(currency));
