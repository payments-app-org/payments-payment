package com.paymentsapp.payment.client;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

// Справочники biller: способы перевода за рубеж, начисления госуслуг, поставщики и операторы связи
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

    // Начисление госуслуг по id — сумму и получателя берём отсюда, а не из запроса
    public Optional<GovCharge> govCharge(java.util.UUID id) {
        try {
            return Optional.ofNullable(rest.get().uri("/api/biller/gov/charges/{id}", id).retrieve().body(GovCharge.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    // Оператор связи по номеру: empty — номер не мобильный или оператор не найден
    public Optional<Provider> mobileOperator(String phone) {
        try {
            return Optional.ofNullable(rest.get().uri("/api/biller/mobile/operator?phone={phone}", phone).retrieve()
                    .body(Provider.class));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            return Optional.empty();
        }
    }

    public Optional<ProviderDetails> provider(String code) {
        try {
            return Optional.ofNullable(rest.get().uri("/api/biller/providers/{code}", code).retrieve()
                    .body(ProviderDetails.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public record Provider(int id, String code, String groupCode, String name, String title, String legalName) {
    }

    public record ProviderDetails(Provider provider, GovDetails gov) {
    }

    public record GovDetails(String lookup, String uinPrefix, String payeeName, String payeeInn, String payeeKpp,
                             String payeeAccount, String payeeBik, String kbk, String oktmo, String purpose) {
    }

    public record GovCharge(java.util.UUID id, String uin, String purpose, String payeeName, String payeeInn,
                            String payeeKpp, String payeeAccount, String payeeBik, String kbk, String oktmo,
                            java.math.BigDecimal payableAmount, String status) {
    }

    public record MethodDetails(Method method, List<String> currencies, List<Field> fields) {
    }

    public record Method(int id, String countryCode, String name, String kind) {
    }

    public record Field(String key, String label, String type, String pattern) {
    }
}
