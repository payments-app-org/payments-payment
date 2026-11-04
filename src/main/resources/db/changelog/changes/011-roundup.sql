--liquibase formatted sql

--changeset payment:011-roundup
-- «Округление на пользу»: сумма покупки округляется вверх до шага, разница уходит в благотворительный фонд.
-- total = amount + commission + round_up_amount; в фонд деньги переводит biller в конце дня.
ALTER TABLE payments ADD COLUMN round_up_amount    NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (round_up_amount >= 0);
ALTER TABLE payments ADD COLUMN round_up_fund_code VARCHAR(40);
ALTER TABLE payments ADD COLUMN round_up_fund_name VARCHAR(150);
ALTER TABLE payments ADD CONSTRAINT payments_round_up_fund CHECK (round_up_amount = 0 OR round_up_fund_code IS NOT NULL);
