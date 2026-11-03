package com.paymentsapp.payment.client;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

// Справочник способов перевода за рубеж: к какой стране относится способ, в каких валютах и какие поля нужны
@Component
public class BillerClient {

    private final RestClient rest;

    public BillerClient(RestClient.Builder builder,
                        @Value("${clients.biller.url}") String url,
                        @Value("${clients.biller.timeout}") Duration timeout) {
        this.rest = RestClients.create(builder, url, timeout);
    }

    public Optional<MethodDetails> method(int id) {
        try {
            return Optional.ofNullable(rest.get().uri("/api/biller/methods/{id}", id).retrieve().body(MethodDetails.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public record MethodDetails(Method method, List<String> currencies, List<Field> fields) {
    }

    public record Method(int id, String countryCode, String name, String kind) {
    }

    public record Field(String key, String label, String type, String pattern) {
    }
}
