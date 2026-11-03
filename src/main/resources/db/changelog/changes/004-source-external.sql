--liquibase formatted sql

--changeset payment:004-source-external
-- Списание с привязанной карты другого банка: деньги забирает процессинг, поэтому даже перевод
-- между своими счетами идёт через антифрод и процессинг
ALTER TABLE payments ADD COLUMN source_external BOOLEAN NOT NULL DEFAULT false;
