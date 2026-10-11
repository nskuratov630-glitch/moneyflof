-- Enforce that ledger rows can only reference wallets owned by the same user.
ALTER TABLE wallet ADD CONSTRAINT uq_wallet_id_user UNIQUE (id, user_id);
ALTER TABLE finance_transaction
  ADD CONSTRAINT fk_transaction_wallet_owner
  FOREIGN KEY (wallet_id, user_id) REFERENCES wallet(id, user_id) ON DELETE RESTRICT;
ALTER TABLE wallet_transfer
  ADD CONSTRAINT fk_transfer_from_wallet_owner
  FOREIGN KEY (from_wallet_id, user_id) REFERENCES wallet(id, user_id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_transfer_to_wallet_owner
  FOREIGN KEY (to_wallet_id, user_id) REFERENCES wallet(id, user_id) ON DELETE RESTRICT;

CREATE INDEX idx_tx_user_type_date ON finance_transaction(user_id, type, occurred_at DESC);
CREATE INDEX idx_tx_user_category_date ON finance_transaction(user_id, category, occurred_at DESC);
CREATE INDEX idx_transfer_user_created ON wallet_transfer(user_id, created_at DESC);
CREATE INDEX idx_budget_user_category_month ON budget(user_id, category, month);
