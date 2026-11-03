--liquibase formatted sql

--changeset payment:003-own-transfer
-- Перевод между своими счетами: получатель — свой же счёт (recipient_account_id), реквизитов нет
ALTER TABLE payments DROP CONSTRAINT payments_type_check;
ALTER TABLE payments ADD CONSTRAINT payments_type_check CHECK (type IN ('SBP_PHONE', 'CARD2CARD', 'OWN_TRANSFER'));
ALTER TABLE payments ADD CONSTRAINT payments_own_transfer_target CHECK (type <> 'OWN_TRANSFER' OR recipient_account_id IS NOT NULL);
