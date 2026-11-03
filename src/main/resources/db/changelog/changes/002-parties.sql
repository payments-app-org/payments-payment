--liquibase formatted sql

--changeset payment:002-parties
-- Стороны перевода. recipient_user_id / recipient_account_id заполнены, если получатель — наш клиент:
-- тогда account зачислит ему деньги вместе со списанием у отправителя.
ALTER TABLE payments ADD COLUMN sender_name VARCHAR(100);
ALTER TABLE payments ADD COLUMN recipient_user_id UUID;
ALTER TABLE payments ADD COLUMN recipient_account_id UUID;
ALTER TABLE payments ADD COLUMN recipient_name VARCHAR(100);
ALTER TABLE payments ADD COLUMN message VARCHAR(40);
