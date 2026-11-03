--liquibase formatted sql

--changeset payment:006-abroad
-- Перевод за рубеж: страна, способ перевода из biller, валюта и сумма к получению по курсу из котировки.
-- Данные получателя храним в JSON; номера карт — только маской.
ALTER TABLE payments DROP CONSTRAINT payments_type_check;
ALTER TABLE payments ADD CONSTRAINT payments_type_check CHECK (type IN ('SBP_PHONE', 'CARD2CARD', 'OWN_TRANSFER', 'ABROAD'));
ALTER TABLE payments ADD COLUMN country VARCHAR(2);
ALTER TABLE payments ADD COLUMN receive_currency VARCHAR(3);
ALTER TABLE payments ADD COLUMN receive_amount NUMERIC(19, 2);
ALTER TABLE payments ADD COLUMN fx_rate NUMERIC(19, 6);
ALTER TABLE payments ADD COLUMN transfer_method_id INTEGER;
ALTER TABLE payments ADD COLUMN transfer_method_name VARCHAR(100);
ALTER TABLE payments ADD COLUMN recipient_details JSONB;
ALTER TABLE payments ADD CONSTRAINT payments_abroad_details CHECK (type <> 'ABROAD' OR
    (country IS NOT NULL AND receive_currency IS NOT NULL AND receive_amount IS NOT NULL AND transfer_method_id IS NOT NULL));
