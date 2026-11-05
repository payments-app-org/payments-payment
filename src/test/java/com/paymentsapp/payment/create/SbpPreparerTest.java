package com.paymentsapp.payment.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class SbpPreparerTest {

    private static final String PHONE = "+79161234567";
    private static final String SBER = "100000000111";
    private static final String TBANK = "100000000004";

    private final AccountClient account = mock(AccountClient.class);
    private final ProcessingClient processing = mock(ProcessingClient.class);
    private final SbpPreparer preparer = new SbpPreparer(account, processing);
    private final UUID userId = UUID.randomUUID();
    private final AccountClient.UserInfo user = new AccountClient.UserInfo("Артур Константинович Р", Map.of(), null);

    private PreparationContext context(String bankId) {
        PaymentRequest req = new PaymentRequest(PaymentType.SBP_PHONE, UUID.randomUUID(), PHONE, bankId, null, null,
                new BigDecimal("500"), null, "token", null, null, null, null, null, null, null, null);
        return new PreparationContext(userId, req, null, user);
    }

    @Test
    void ourClientWithoutChosenBankGetsDefaultBank() {
        UUID recipient = UUID.randomUUID();
        when(account.findRecipientByPhone(PHONE)).thenReturn(Optional.of(
                new AccountClient.Recipient(recipient, "Мария Сергеевна И", UUID.randomUUID(), SBER, null)));
        when(processing.sbpBanks(PHONE)).thenReturn(List.of(SBER, TBANK));

        Prepared prepared = preparer.prepare(context(null));

        assertThat(prepared.recipientBankId()).isEqualTo(SBER);
        assertThat(prepared.parties().recipientUserId()).isEqualTo(recipient);
    }

    @Test
    void bankWithoutRecipientAccountIsRejected() {
        when(account.findRecipientByPhone(PHONE)).thenReturn(Optional.empty());
        when(processing.sbpBanks(PHONE)).thenReturn(List.of(SBER));

        assertThatThrownBy(() -> preparer.prepare(context(TBANK))).hasMessage("У получателя нет счёта в этом банке");
    }

    @Test
    void unknownBankForExternalRecipient() {
        when(account.findRecipientByPhone(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> preparer.prepare(context(null))).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode().code()).isEqualTo("RECIPIENT_BANK_UNKNOWN");
    }

    @Test
    void accountOutageBecomesServiceUnavailable() {
        when(account.findRecipientByPhone(PHONE)).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> preparer.prepare(context(SBER)))
                .extracting(e -> ((DomainException) e).getCode().code()).isEqualTo("SERVICE_UNAVAILABLE");
    }
}
