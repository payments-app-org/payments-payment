package com.paymentsapp.payment.payment;

import static com.paymentsapp.payment.payment.PaymentStatus.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.client.AccountRejectedException;
import com.paymentsapp.payment.client.FraudClient;
import com.paymentsapp.payment.client.FraudClient.FraudDecision;
import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.client.ProcessingClient.TransferResult;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PaymentSagaTest {

    private final PaymentStore store = mock(PaymentStore.class);
    private final AccountClient account = mock(AccountClient.class);
    private final FraudClient fraud = mock(FraudClient.class);
    private final ProcessingClient processing = mock(ProcessingClient.class);
    private final PaymentSaga saga = new PaymentSaga(store, account, fraud, processing);

    private final Payment payment = new Payment(UUID.randomUUID(), PaymentType.SBP_PHONE, UUID.randomUUID(),
            "+79161234567", "100000000004", null, new BigDecimal("100"), new BigDecimal("1"), 1, "RUB",
            UUID.randomUUID());
    private final UUID id = payment.getId();

    @BeforeEach
    void storeReturnsPayment() {
        when(store.move(any(), any(), anyString())).thenReturn(payment);
        when(store.moveWithProcessingId(any(), any(), any())).thenReturn(payment);
        when(store.fail(any(), any(), any(), any())).thenReturn(payment);
        when(store.get(id)).thenReturn(payment);
    }

    @Test
    void happyPathReservesChecksProcessesAndCaptures() {
        when(fraud.check(payment)).thenReturn(new FraudDecision("APPROVED", null));
        when(processing.transfer(payment, null)).thenReturn(new TransferResult("COMPLETED", "PRC-1", null));

        saga.run(payment, null);

        var order = inOrder(account, fraud, processing, store);
        order.verify(account).reserve(payment);
        order.verify(store).move(eq(id), eq(RESERVED), anyString());
        order.verify(fraud).check(payment);
        order.verify(store).move(eq(id), eq(FRAUD_APPROVED), anyString());
        order.verify(store).move(eq(id), eq(SENT_TO_PROCESSING), anyString());
        order.verify(processing).transfer(payment, null);
        order.verify(store).moveWithProcessingId(id, CONFIRMING, "PRC-1");
        order.verify(account).capture(id);
        order.verify(store).move(eq(id), eq(SUCCESS), anyString());
        verify(account, never()).release(any());
    }

    @Test
    void businessRejectOnReserveFailsWithoutCompensation() {
        org.mockito.Mockito.doThrow(new AccountRejectedException("LIMIT_EXCEEDED", "Превышен дневной лимит"))
                .when(account).reserve(payment);

        saga.run(payment, null);

        verify(store).fail(id, FAILED, FailureCode.LIMIT_EXCEEDED, "Превышен дневной лимит");
        verify(account, never()).release(any());
        verify(fraud, never()).check(any());
    }

    @Test
    void accountTimeoutOnReserveReleasesJustInCase() {
        org.mockito.Mockito.doThrow(new ResourceAccessException("timeout")).when(account).reserve(payment);

        saga.run(payment, null);

        verify(store).fail(eq(id), eq(COMPENSATING), eq(FailureCode.ACCOUNT_UNAVAILABLE), anyString());
        verify(account).release(id);
        verify(store).move(eq(id), eq(FAILED), anyString());
    }

    @Test
    void fraudRejectReleasesReservation() {
        when(fraud.check(payment)).thenReturn(new FraudDecision("REJECTED", "Получатель в стоп-листе"));

        saga.run(payment, null);

        verify(store).fail(id, COMPENSATING, FailureCode.FRAUD_REJECTED, "Получатель в стоп-листе");
        verify(account).release(id);
        verify(store).move(eq(id), eq(FAILED), anyString());
        verify(processing, never()).transfer(any(), any());
    }

    @Test
    void fraudTimeoutReleasesReservation() {
        when(fraud.check(payment)).thenThrow(new ResourceAccessException("Read timed out"));

        saga.run(payment, null);

        verify(store).fail(eq(id), eq(COMPENSATING), eq(FailureCode.FRAUD_UNAVAILABLE), anyString());
        verify(account).release(id);
    }

    @Test
    void processingDeclineReleasesReservation() {
        when(fraud.check(payment)).thenReturn(new FraudDecision("APPROVED", null));
        when(processing.transfer(payment, null)).thenReturn(new TransferResult("DECLINED", null, "Получатель не найден"));

        saga.run(payment, null);

        verify(store).fail(id, COMPENSATING, FailureCode.PROCESSING_DECLINED, "Получатель не найден");
        verify(account).release(id);
        verify(account, never()).capture(any());
    }

    @Test
    void processingTimeoutAsksForStatusAndCapturesIfCompleted() {
        when(fraud.check(payment)).thenReturn(new FraudDecision("APPROVED", null));
        when(processing.transfer(payment, null)).thenThrow(new ResourceAccessException("Read timed out"));
        when(processing.findResult(id)).thenReturn(Optional.of(new TransferResult("COMPLETED", "PRC-2", null)));

        saga.run(payment, null);

        verify(account).capture(id);
        verify(store).move(eq(id), eq(SUCCESS), anyString());
    }

    @Test
    void processingTimeoutWithUnknownOutcomeLeavesPaymentForRecovery() {
        when(fraud.check(payment)).thenReturn(new FraudDecision("APPROVED", null));
        when(processing.transfer(payment, null)).thenThrow(new ResourceAccessException("Read timed out"));
        when(processing.findResult(id)).thenReturn(Optional.empty());

        saga.run(payment, null);

        // Деньги могли уйти — ни списываем, ни снимаем резерв, пока не узнаем исход
        verify(account, never()).capture(any());
        verify(account, never()).release(any());
    }

    @Test
    void captureFailureLeavesPaymentConfirmingForRetry() {
        when(fraud.check(payment)).thenReturn(new FraudDecision("APPROVED", null));
        when(processing.transfer(payment, null)).thenReturn(new TransferResult("COMPLETED", "PRC-3", null));
        org.mockito.Mockito.doThrow(new ResourceAccessException("down")).when(account).capture(id);

        saga.run(payment, null);

        verify(store, never()).move(eq(id), eq(SUCCESS), anyString());
        verify(account, never()).release(any());
    }
}
