package com.paymentsapp.payment.web;

import static com.paymentsapp.payment.web.PaymentException.Code.INVALID_REQUISITES;

import com.paymentsapp.payment.client.BillerClient.GovDetails;
import com.paymentsapp.payment.client.BillerClient.ProviderDetails;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

// Госпошлина по УИН из квитанции (Росреестр): плательщик вводит УИН, ФИО, адрес, телефон и сумму.
// Получатель — из карточки поставщика в biller, назначение — его же.
final class GovFee {

    private GovFee() {
    }

    static RuRequisites.Checked check(PaymentRequest req, ProviderDetails provider, JsonMapper json) {
        GovDetails gov = provider.gov();
        require("FREE".equals(gov.lookup()), "У этого получателя оплата только по найденному начислению");
        Map<String, String> f = req.recipientFields();
        String uin = f.getOrDefault("uin", "").replaceAll("\\D", "");
        String payerName = f.getOrDefault("payerName", "").trim();
        String payerAddress = f.getOrDefault("payerAddress", "").trim();
        String payerPhone = f.getOrDefault("payerPhone", "").trim();

        require(uin.matches("\\d{20}|\\d{25}"), "УИН — 20 или 25 цифр");
        require(gov.uinPrefix() == null || uin.startsWith(gov.uinPrefix()),
                "УИН этого получателя начинается с " + gov.uinPrefix());
        require(!payerName.isEmpty() && payerName.length() <= 150, "Укажите ФИО плательщика");
        require(!payerAddress.isEmpty() && payerAddress.length() <= 255, "Укажите адрес плательщика");
        require(payerPhone.matches("\\+7\\d{10}"), "Телефон плательщика в формате +7XXXXXXXXXX");

        Map<String, String> details = new LinkedHashMap<>();
        details.put("uin", uin);
        details.put("name", gov.payeeName());
        details.put("inn", gov.payeeInn());
        details.put("kpp", gov.payeeKpp());
        details.put("account", gov.payeeAccount());
        details.put("bik", gov.payeeBik());
        details.put("kbk", gov.kbk());
        details.put("oktmo", gov.oktmo());
        details.put("payerName", payerName);
        details.put("payerAddress", payerAddress);
        details.put("payerPhone", payerPhone);
        return new RuRequisites.Checked(json.writeValueAsString(details), gov.payeeName(), gov.purpose());
    }

    // +79964480061 → +7 996 448-80-61
    static String formatPhone(String phone) {
        return "+7 " + phone.substring(2, 5) + " " + phone.substring(5, 8) + "-" + phone.substring(8, 10) + "-" + phone.substring(10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new PaymentException(INVALID_REQUISITES, message);
        }
    }
}
