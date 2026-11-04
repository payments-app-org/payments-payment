--liquibase formatted sql

--changeset payment:010-bill
-- Всё, что платим через biller (связь, госуслуги, транспорт, игры, зарубежные операторы), — один тип BILL.
-- Поставщик и его группа — отдельными колонками, введённые плательщиком данные — bill_fields:
-- [{key, label, value, display}] (у каждого поставщика свой набор полей).
ALTER TABLE payments ADD COLUMN provider_code  VARCHAR(40);
ALTER TABLE payments ADD COLUMN provider_group VARCHAR(30);
ALTER TABLE payments ADD COLUMN bill_fields    JSONB;

ALTER TABLE payments DROP CONSTRAINT payments_type_check;
ALTER TABLE payments DROP CONSTRAINT payments_requisites_details;
ALTER TABLE payments DROP CONSTRAINT payments_mobile_details;
DROP INDEX payments_bill_once_uq;

-- Прежние платежи связи и госуслуг переводим в BILL
UPDATE payments SET provider_code = recipient_details ->> 'provider', provider_group = 'MOBILE',
    bill_fields = jsonb_build_array(jsonb_build_object('key', 'phone', 'label', 'Номер телефона',
        'value', recipient_phone, 'display', recipient_phone)),
    type = 'BILL'
WHERE type = 'MOBILE_PAYMENT';
UPDATE payments SET provider_code = CASE WHEN bill_reference LIKE '%/%' THEN 'uin' ELSE 'rosreestr' END,
    provider_group = 'GOV',
    bill_fields = jsonb_build_array(jsonb_build_object('key', 'uin', 'label', 'УИН',
        'value', recipient_details ->> 'uin', 'display', recipient_details ->> 'uin')),
    type = 'BILL'
WHERE type = 'UIN_PAYMENT';

ALTER TABLE payments ADD CONSTRAINT payments_type_check CHECK (type IN
    ('SBP_PHONE', 'CARD2CARD', 'OWN_TRANSFER', 'ABROAD', 'WALLET_TRANSFER', 'REQUISITES', 'REQUISITES_OUR', 'BILL'));
ALTER TABLE payments ADD CONSTRAINT payments_requisites_details CHECK
    (type NOT IN ('REQUISITES', 'REQUISITES_OUR') OR (recipient_details IS NOT NULL AND purpose IS NOT NULL));
ALTER TABLE payments ADD CONSTRAINT payments_bill_details CHECK
    (type <> 'BILL' OR (provider_code IS NOT NULL AND provider_group IS NOT NULL AND bill_fields IS NOT NULL AND purpose IS NOT NULL));

-- Одно начисление / один УИН — один живой платёж
CREATE UNIQUE INDEX payments_bill_once_uq ON payments (bill_reference)
    WHERE type = 'BILL' AND bill_reference IS NOT NULL AND status <> 'FAILED';
