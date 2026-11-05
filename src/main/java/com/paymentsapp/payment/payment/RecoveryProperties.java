package com.paymentsapp.payment.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Через сколько платёж в промежуточном статусе считается зависшим. Больше, чем длится обычный проход саги,
// чтобы не пересечься с живым запросом. acceptedCheckAfter — как часто спрашивать итог долгого перевода
// (после каждого вопроса отсчёт начинается заново). batchSize — сколько платежей одного статуса дожимаем за проход.
@ConfigurationProperties("payment.recovery")
public record RecoveryProperties(Duration interval, Duration confirmAfter, Duration releaseAfter, Duration processingAfter,
                                 Duration acceptedCheckAfter, Duration interruptedAfter, int batchSize) {
}
