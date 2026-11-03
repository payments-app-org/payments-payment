package com.paymentsapp.payment.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Клиенты с таймаутами: сага не должна ждать зависший сервис бесконечно
final class RestClients {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);

    private RestClients() {
    }

    static RestClient create(RestClient.Builder builder, String baseUrl, Duration readTimeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
