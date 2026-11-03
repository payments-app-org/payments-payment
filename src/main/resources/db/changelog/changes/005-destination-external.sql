--liquibase formatted sql

--changeset payment:005-destination-external
-- Зачисление на свою привязанную карту другого банка: деньги доставляет процессинг
ALTER TABLE payments ADD COLUMN destination_external BOOLEAN NOT NULL DEFAULT false;
