--liquibase formatted sql

--changeset payment:008-bill-once
-- Одно начисление по УИН — один живой платёж: повторную оплату не даём, пока первая не провалилась
CREATE UNIQUE INDEX payments_bill_once_uq ON payments (bill_reference)
    WHERE type = 'UIN_PAYMENT' AND status <> 'FAILED';
