package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.create.Checks.require;
import static com.paymentsapp.payment.error.PaymentError.*;

import com.paymentsapp.payment.client.BillerClient;
import com.paymentsapp.payment.client.BillerClient.Field;
import com.paymentsapp.payment.client.BillerClient.MethodDetails;
import com.paymentsapp.payment.payment.AbroadDetails;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.payment.quote.Quote;
import com.paymentsapp.platform.error.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

// Перевод за рубеж по справочнику biller: способ из этой страны, валюта доступна, котировка на ту же сумму
// к получению, поля получателя по шаблону способа. Номер карты — процессингу, в базе — маской.
@Component
class AbroadPreparer implements PaymentPreparer {

    private static final Pattern SPACES = Pattern.compile("\\s");
    private static final String FIRST_NAME = "first_name";
    private static final String LAST_NAME = "last_name";
    private static final String NAME_SUFFIX = "_name";

    private final BillerClient biller;
    private final JsonMapper json;

    AbroadPreparer(BillerClient biller, JsonMapper json) {
        this.biller = biller;
        this.json = json;
    }

    @Override
    public Set<PaymentType> types() {
        return Set.of(PaymentType.ABROAD);
    }

    @Override
    public void validate(PaymentRequest req) {
        require(req.country() != null && req.receiveCurrency() != null && req.receiveAmount() != null
                && req.transferMethodId() != null, ABROAD_PARAMS_REQUIRED);
        require(req.recipientPhone() == null && req.recipientBankId() == null && req.recipientCardNumber() == null
                && req.destinationAccountId() == null, ABROAD_FIELDS_ONLY);
    }

    @Override
    public Prepared prepare(PreparationContext ctx) {
        PaymentRequest req = ctx.request();
        Quote quote = ctx.quote();
        MethodDetails details;
        try {
            details = biller.method(req.transferMethodId()).orElseThrow(() -> new DomainException(METHOD_NOT_FOUND));
        } catch (RestClientException e) {
            throw DomainException.withCause(METHODS_UNAVAILABLE, e);
        }
        require(details.method().countryCode().equalsIgnoreCase(req.country()), METHOD_COUNTRY);
        require(details.currencies().contains(req.receiveCurrency()), CURRENCY_NOT_ALLOWED, req.receiveCurrency());
        require(req.receiveCurrency().equals(quote.receiveCurrency()) && quote.receiveAmount() != null
                && quote.receiveAmount().compareTo(req.receiveAmount()) == 0, FX_QUOTE_MISMATCH);

        Map<String, String> given = req.recipientFields() == null ? Map.of() : req.recipientFields();
        Map<String, String> stored = new LinkedHashMap<>();
        String cardNumber = null;
        for (Field f : details.fields()) {
            String value = normalize(f, given.get(f.key()));
            require(value != null && !value.isEmpty(), FIELD_REQUIRED, f.label());
            boolean card = f.type() == BillerClient.FieldType.CARD_NUMBER;
            require(Pattern.matches(f.pattern(), value) && (!card || CardNumbers.luhn(value)), FIELD_INVALID, f.label());
            if (card) {
                cardNumber = value;
                stored.put(f.key(), CardNumbers.mask(value));
            } else {
                stored.put(f.key(), value);
            }
        }
        String first = stored.get(FIRST_NAME);
        String last = stored.get(LAST_NAME);
        String name = first != null && last != null ? first + " " + last : details.method().name();
        AbroadDetails abroad = new AbroadDetails(req.country().toUpperCase(), req.receiveCurrency(), quote.receiveAmount(),
                quote.fxRate(), details.method().id(), details.method().name());
        return Prepared.builder(ctx)
                .recipientName(name)
                .abroad(abroad)
                .payee(new Prepared.Payee(json.writeValueAsString(stored), null, null))
                .cardForProcessing(cardNumber)
                .build();
    }

    // Номера, IBAN, SWIFT, IFSC и телефоны — без пробелов и заглавными; имена латиницей — заглавными
    private static String normalize(Field f, String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        return switch (f.type()) {
            case CARD_NUMBER, PHONE, ACCOUNT, IBAN, SWIFT, IFSC -> SPACES.matcher(v).replaceAll("").toUpperCase();
            case TEXT -> f.key().endsWith(NAME_SUFFIX) ? v.toUpperCase() : v;
        };
    }
}
