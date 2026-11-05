package com.paymentsapp.payment.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// retention — сколько хранить ключ: дольше, чем клиент может повторять запрос с ним (после удаления повтор
// с тем же ключом создал бы новый платёж). cleanupBatch — сколько строк удалять одним запросом.
@ConfigurationProperties("payment.idempotency")
public record IdempotencyProperties(Duration retention, Duration cleanupInterval, int cleanupBatch) {
}
