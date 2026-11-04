--liquibase formatted sql

--changeset payment:009-mobile
-- Мобильная связь: оператор и номер — в recipient_details, номер — ещё и в recipient_phone
ALTER TABLE payments DROP CONSTRAINT payments_type_check;
ALTER TABLE payments ADD CONSTRAINT payments_type_check CHECK (type IN
    ('SBP_PHONE', 'CARD2CARD', 'OWN_TRANSFER', 'ABROAD', 'WALLET_TRANSFER', 'REQUISITES', 'REQUISITES_OUR', 'UIN_PAYMENT',
     'MOBILE_PAYMENT'));
ALTER TABLE payments ADD CONSTRAINT payments_mobile_details CHECK
    (type <> 'MOBILE_PAYMENT' OR (recipient_details IS NOT NULL AND recipient_phone IS NOT NULL));
