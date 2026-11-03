--liquibase formatted sql

--changeset payment:001-payments
CREATE TABLE payments (
    id                    UUID PRIMARY KEY,
    user_id               UUID           NOT NULL,
    type                  VARCHAR(20)    NOT NULL CHECK (type IN ('SBP_PHONE', 'CARD2CARD')),
    status                VARCHAR(30)    NOT NULL CHECK (status IN (
        'CREATED', 'RESERVED', 'FRAUD_APPROVED', 'SENT_TO_PROCESSING',
        'CONFIRMING', 'SUCCESS', 'COMPENSATING', 'FAILED')),
    source_account_id     UUID           NOT NULL,
    recipient_phone       VARCHAR(20),
    recipient_bank_id     VARCHAR(12),
    -- Полный номер карты не храним, только маску
    recipient_card_mask   VARCHAR(25),
    amount                NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    commission_amount     NUMERIC(19, 2) NOT NULL CHECK (commission_amount >= 0),
    commission_tariff_id  INTEGER        NOT NULL,
    total                 NUMERIC(19, 2) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    quote_id              UUID           NOT NULL,
    processing_id         VARCHAR(100),
    failure_code          VARCHAR(40),
    failure_reason        VARCHAR(255),
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (type <> 'SBP_PHONE' OR (recipient_phone IS NOT NULL AND recipient_bank_id IS NOT NULL)),
    CHECK (type <> 'CARD2CARD' OR recipient_card_mask IS NOT NULL)
);

-- Котировку можно использовать только один раз
CREATE UNIQUE INDEX payments_quote_id_uq ON payments (quote_id);
CREATE INDEX payments_user_id_idx ON payments (user_id, created_at DESC);
-- Для планировщика, который дожимает зависшие платежи
CREATE INDEX payments_in_progress_idx ON payments (status, updated_at) WHERE status NOT IN ('SUCCESS', 'FAILED');

--changeset payment:001-status-history
-- След саги: каждый переход статуса с пояснением
CREATE TABLE payment_status_history (
    id                    BIGSERIAL PRIMARY KEY,
    payment_id            UUID         NOT NULL REFERENCES payments (id),
    status                VARCHAR(30)  NOT NULL,
    note                  VARCHAR(255),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX payment_status_history_payment_idx ON payment_status_history (payment_id, id);

--changeset payment:001-idempotency-keys
-- Ключ захватывается вставкой ON CONFLICT DO NOTHING до любой другой работы.
-- Для отказов до создания платежа сохраняем готовый ответ; если платёж создан — отвечаем по его текущему состоянию.
CREATE TABLE idempotency_keys (
    key                   VARCHAR(100) PRIMARY KEY,
    user_id               UUID         NOT NULL,
    request_hash          VARCHAR(64)  NOT NULL,
    payment_id            UUID REFERENCES payments (id),
    response_status       INTEGER,
    response_body         JSONB,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

--changeset payment:001-outbox
CREATE TABLE outbox (
    id                    UUID PRIMARY KEY,
    topic                 VARCHAR(100) NOT NULL,
    aggregate_id          VARCHAR(100) NOT NULL,
    event_type            VARCHAR(50)  NOT NULL,
    payload               JSONB        NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at          TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;
