--liquibase formatted sql

--changeset payment:007-wallet-requisites-uin
-- На кошелёк другого клиента, по реквизитам юрлицу (в другой банк / в наш), госуслуги по УИН.
-- Реквизиты получателя — в recipient_details (JSON), назначение платежа — в purpose.
ALTER TABLE payments DROP CONSTRAINT payments_type_check;
ALTER TABLE payments ADD CONSTRAINT payments_type_check CHECK (type IN
    ('SBP_PHONE', 'CARD2CARD', 'OWN_TRANSFER', 'ABROAD', 'WALLET_TRANSFER', 'REQUISITES', 'REQUISITES_OUR', 'UIN_PAYMENT'));
ALTER TABLE payments ADD COLUMN purpose VARCHAR(255);
-- УИН и id начисления в biller — для оплаты госуслуг
ALTER TABLE payments ADD COLUMN bill_reference VARCHAR(60);
ALTER TABLE payments ADD CONSTRAINT payments_requisites_details CHECK
    (type NOT IN ('REQUISITES', 'REQUISITES_OUR', 'UIN_PAYMENT') OR (recipient_details IS NOT NULL AND purpose IS NOT NULL));
