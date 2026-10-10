-- Phase 7 (ARCHITECTURE.md C.3, E.8): chk_tx_transfer_pair could not be satisfied by the
-- transfer write path. Hibernate assigns both IDENTITY ids by saving the legs first, then
-- cross-links them in the same transaction - so the first INSERT has neither a sibling id
-- nor a counterparty. Requiring transfer_group_id keeps the pairing information at insert
-- time; counterparty_transaction_id is populated before commit and is still guaranteed to
-- reference a real ledger row by fk_tx_counterparty.
ALTER TABLE transactions DROP CHECK chk_tx_transfer_pair;
ALTER TABLE transactions ADD CONSTRAINT chk_tx_transfer_pair
        CHECK (type <> 'TRANSFER' OR transfer_group_id IS NOT NULL);
