--liquibase formatted sql

--changeset payment:013-accepted-status
-- ACCEPTED: процессинг (или поставщик) принял платёж, итог придёт позже — перевод за рубеж, пополнение из другого банка
-- по СБП (ждём подтверждения клиента там), подписка через партнёра
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK (status IN (
    'CREATED', 'RESERVED', 'FRAUD_APPROVED', 'SENT_TO_PROCESSING', 'ACCEPTED',
    'CONFIRMING', 'SUCCESS', 'COMPENSATING', 'FAILED'));

--changeset payment:013-incoming-sbp
-- Входящий перевод по СБП из другого банка: источника-счёта у нас нет, комиссии и котировки нет,
-- processing_id — номер операции в СБП (по нему повтор от СБП не зачислит деньги дважды)
ALTER TABLE payments DROP CONSTRAINT payments_type_check;
ALTER TABLE payments ADD CONSTRAINT payments_type_check CHECK (type IN
    ('SBP_PHONE', 'CARD2CARD', 'OWN_TRANSFER', 'ABROAD', 'WALLET_TRANSFER', 'REQUISITES', 'REQUISITES_OUR', 'BILL',
     'INCOMING_SBP'));
ALTER TABLE payments ALTER COLUMN source_account_id DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN quote_id DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN commission_tariff_id DROP NOT NULL;
ALTER TABLE payments ADD CONSTRAINT payments_outgoing_details CHECK (type = 'INCOMING_SBP' OR
    (source_account_id IS NOT NULL AND quote_id IS NOT NULL AND commission_tariff_id IS NOT NULL));
ALTER TABLE payments ADD CONSTRAINT payments_incoming_details CHECK (type <> 'INCOMING_SBP' OR
    (processing_id IS NOT NULL AND recipient_account_id IS NOT NULL));
CREATE UNIQUE INDEX payments_incoming_once_uq ON payments (processing_id) WHERE type = 'INCOMING_SBP';
-- Банк, откуда пришли деньги: входящий СБП и пополнение со своего счёта в другом банке
ALTER TABLE payments ADD COLUMN source_bank_id VARCHAR(12);

--changeset payment:013-idempotency-cleanup
-- Очистка ключей идемпотентности старше срока хранения (IdempotencyCleanup)
CREATE INDEX idempotency_keys_created_idx ON idempotency_keys (created_at);
