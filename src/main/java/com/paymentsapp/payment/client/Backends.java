package com.paymentsapp.payment.client;

// Имена соседей: группы HTTP-клиентов (spring.http.serviceclient.*) и экземпляров Resilience4j (resilience4j.*)
final class Backends {

    static final String ACCOUNT = "account";
    static final String FRAUD = "fraud";
    static final String PROCESSING = "processing";
    static final String BILLER = "biller";
    // Повтор только при ошибке соединения: запрос точно не дошёл, деньги не могли уйти дважды
    static final String PROCESSING_CONNECT_ONLY = "processing-connect-only";
    static final String BILLER_CONNECT_ONLY = "biller-connect-only";

    private Backends() {
    }
}
