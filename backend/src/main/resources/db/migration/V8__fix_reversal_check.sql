-- SpendIO V8 - uninsertable reversal CHECK (ARCHITECTURE.md C.3 #10, D.8)
--
-- V6 landed two checks that cannot both hold for the mirrored entry of a reversal:
--   chk_tx_reversal_marked        requires reversal_of -> reversed_at NOT NULL
--   chk_tx_original_not_reversed  forbids both columns being set on one row
-- Together they reject every row with reversal_of_transaction_id (rule 2 forces
-- reversed_at, rule 1 then rejects the pair), so reversals could never be written.
-- The intended split - stated right above the pair in V6 - is that the original row
-- carries reversed_at and the mirrored entry carries reversal_of, never both;
-- chk_tx_original_not_reversed alone already enforces that a row cannot be both a
-- reversal and a reversed original, so the second rule is the redundant (and
-- contradictory) one and is dropped.

ALTER TABLE transactions DROP CHECK chk_tx_reversal_marked;
