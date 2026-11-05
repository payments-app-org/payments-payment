package com.paymentsapp.payment.payment;

import static com.paymentsapp.payment.payment.PaymentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.paymentsapp.payment.client.BillerClient;
import com.paymentsapp.payment.client.FraudClient;
import com.paymentsapp.payment.client.FraudClient.FraudDecision;
import com.paymentsapp.payment.client.FraudClient.Verdict;
import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.client.ProcessingClient.TransferResult;
import com.paymentsapp.payment.client.ProcessingClient.TransferStatus;
import com.paymentsapp.payment.client.ResultLookup;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

class PaymentSagaTest {

    private final PaymentStore store = mock(PaymentStore.class);
    private final AccountClient account = mock(AccountClient.class);
    private final FraudClient fraud = mock(FraudClient.class);
    private final ProcessingClient processing = mock(ProcessingClient.class);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T12:00:00+03:00");
    private final PaymentSaga saga = new PaymentSaga(store, account, fraud, processing, mock(BillerClient.class),
            new SettlementPolicy(Set.of("DONATIONS")), JsonMapper.builder().build());

    private final Payment payment = new Payment(UUID.randomUUID(), PaymentType.SBP_PHONE, UUID.randomUUID(),
            new Payment.Parties(null, null, null, null), "+79161234567", "100000000004", null, new BigDecimal("100"),
            new BigDecimal("1"), 1, "RUB", UUID.randomUUID(), null, NOW);
    private final UUID id = payment.getId();

    @BeforeEach
    void storeReturnsPayment() {
        when(store.touch(any())).thenReturn(payment);
        when(store.move(any(), any(), anyString())).thenReturn(payment);
        when(store.moveWithProcessingId(any(), any(), any(), anyString())).thenReturn(payment);
        when(store.fail(any(), any(), any(), any())).thenReturn(payment);
        when(store.get(id)).thenReturn(payment);
    }

    @Test
    void happyPathReservesChecksProcessesAndCaptures() {
        when(fraud.check(payment)).thenReturn(new FraudDecision(Verdict.APPROVED, null));
        when(processing.transfer(payment, null)).thenReturn(new TransferResult(TransferStatus.COMPLETED, "PRC-1", null));

        saga.run(payment, null);

        var order = inOrder(account, fraud, processing, store);
        order.verify(account).reserve(payment);
        order.verify(store).move(eq(id), eq(RESERVED), anyString());
        order.verify(fraud).check(payment);
        order.verify(store).move(eq(id), eq(FRAUD_APPROVED), anyString());
        order.verify(store).move(eq(id), eq(SENT_TO_PROCESSING), anyString());
        order.verify(processing).transfer(payment, null);
        order.verify(store).moveWithProcessingId(eq(id), eq(CONFIRMING), eq("PRC-1"), anyString());
        order.verify(account).capture(id);
        order.verify(store).move(eq(id), eq(SUCCESS), anyString());
        verify(account, never()).release(any());
    }

    @Test
    void ownTransferSkipsFraudAndProcessing() {
        Payment own = new Payment(UUID.randomUUID(), PaymentType.OWN_TRANSFER, UUID.randomUUID(),
                new Payment.Parties("Зарплатная ·· 3752", UUID.randomUUID(), UUID.randomUUID(), "Мой кошелёк"),
                null, null, null, new BigDecimal("1000"), new BigDecimal("10"), 1, "RUB", UUID.randomUUID(), null, NOW);
        UUID ownId = own.getId();
        when(store.move(eq(ownId), any(), anyString())).thenReturn(own);

        saga.run(own, null);

        // Одна операция в account: без резерва, антифрода и процессинга
        verify(account).transfer(own);
        verify(store).move(eq(ownId), eq(SUCCESS), anyString());
        verify(account, never()).reserve(any());
        verify(account, never()).capture(any());
        verify(fraud, never()).check(any());
        verify(processing, never()).transfer(any(), any());
    }

    @Test
    void ownTransferTimeoutLeavesPaymentCreatedForRetry() {
        Payment own = new Payment(UUID.randomUUID(), PaymentType.OWN_TRANSFER, UUID.randomUUID(),
                new Payment.Parties("Зарплатная ·· 3752", UUID.randomUUID(), UUID.randomUUID(), "Мой кошелёк"),
                null, null, null, new BigDecimal("1000"), BigDecimal.ZERO, 1, "RUB", UUID.randomUUID(), null, NOW);
        when(store.get(own.getId())).thenReturn(own);
        org.mockito.Mockito.doThrow(new ResourceAccessException("timeout")).when(account).transfer(own);

        Payment result = saga.run(own, null);

        // Неизвестно, прошёл ли перевод: не отменяем (резерва нет, отменять нечего), RecoveryJob повторит перевод
        assertThat(result.getStatus()).isEqualTo(CREATED);
        verify(store, never()).fail(any(), any(), any(), any());
    }

    @Test
    void ownTransferFromLinkedCardGoesThroughFraudAndProcessing() {
        Payment own = new Payment(UUID.randomUUID(), PaymentType.OWN_TRANSFER, UUID.randomUUID(),
                new Payment.Parties("Карта другого банка ·· 1111", UUID.randomUUID(), UUID.randomUUID(), "Мой кошелёк", true),
                null, null, null, new BigDecimal("1000"), new BigDecimal("30"), 1, "RUB", UUID.randomUUID(), null, NOW);
        UUID ownId = own.getId();
        when(store.move(eq(ownId), any(), anyString())).thenReturn(own);
        when(store.moveWithProcessingId(any(), any(), any(), anyString())).thenReturn(own);
        when(fraud.check(own)).thenReturn(new FraudDecision(Verdict.APPROVED, null));
        when(processing.transfer(own, null)).thenReturn(new TransferResult(TransferStatus.COMPLETED, "PRC-9", null));

        saga.run(own, null);

        verify(fraud).check(own);
        verify(processing).transfer(own, null);
        verify(account).capture(ownId);
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
        when(fraud.check(payment)).thenReturn(new FraudDecision(Verdict.REJECTED, "Получатель в стоп-листе"));

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
        when(fraud.check(payment)).thenReturn(new FraudDecision(Verdict.APPROVED, null));
        when(processing.transfer(payment, null)).thenReturn(new TransferResult(TransferStatus.DECLINED, null, "Получатель не найден"));

        saga.run(payment, null);

        verify(store).fail(id, COMPENSATING, FailureCode.PROCESSING_DECLINED, "Получатель не найден");
        verify(account).release(id);
        verify(account, never()).capture(any());
    }

    @Test
    void processingTimeoutAsksForStatusAndCapturesIfCompleted() {
        when(fraud.check(payment)).thenReturn(new FraudDecision(Verdict.APPROVED, null));
        when(processing.transfer(payment, null)).thenThrow(new ResourceAccessException("Read timed out"));
        when(processing.findResult(id)).thenReturn(new ResultLookup.Found(new TransferResult(TransferStatus.COMPLETED, "PRC-2", null)));

        saga.run(payment, null);

        verify(account).capture(id);
        verify(store).move(eq(id), eq(SUCCESS), anyString());
    }

    @Test
    void processingTimeoutWithUnknownOutcomeLeavesPaymentForRecovery() {
        when(fraud.check(payment)).thenReturn(new FraudDecision(Verdict.APPROVED, null));
        when(processing.transfer(payment, null)).thenThrow(new ResourceAccessException("Read timed out"));
        when(processing.findResult(id)).thenReturn(new ResultLookup.NotReceived());

        saga.run(payment, null);

        // Деньги могли уйти — ни списываем, ни снимаем резерв, пока не узнаем исход
        verify(account, never()).capture(any());
        verify(account, never()).release(any());
    }

    @Test
    void captureFailureLeavesPaymentConfirmingForRetry() {
        when(fraud.check(payment)).thenReturn(new FraudDecision(Verdict.APPROVED, null));
        when(processing.transfer(payment, null)).thenReturn(new TransferResult(TransferStatus.COMPLETED, "PRC-3", null));
        org.mockito.Mockito.doThrow(new ResourceAccessException("down")).when(account).capture(id);

        saga.run(payment, null);

        verify(store, never()).move(eq(id), eq(SUCCESS), anyString());
        verify(account, never()).release(any());
    }

    // Найденная ошибка: процессинг лежит дольше processing-after — раньше RecoveryJob возвращал деньги,
    // хотя перевод мог пройти. «Не удалось узнать» ≠ «не получали».
    @Test
    void unknownOutcomeNeverReleasesReservation() {
        Payment sent = sentToProcessing();

        saga.afterLookup(sent, new ResultLookup.Unknown("Connection refused"));

        verify(account, never()).release(any());
        verify(account, never()).capture(any());
        verify(store, never()).fail(any(), any(), any(), any());
        verify(store).touch(sent.getId());
    }

    @Test
    void notReceivedReleasesReservation() {
        Payment sent = sentToProcessing();

        saga.afterLookup(sent, new ResultLookup.NotReceived());

        verify(store).fail(sent.getId(), COMPENSATING, FailureCode.PROCESSING_NOT_COMPLETED,
                "Перевод не был проведён, средства возвращены");
        verify(account).release(sent.getId());
    }

    // Процессинг уже принял перевод (ACCEPTED), а потом говорит «не знаю такой» — противоречие, деньги не трогаем
    @Test
    void notReceivedAfterAcceptedDoesNotReleaseReservation() {
        Payment accepted = sentToProcessing();
        accepted.moveTo(ACCEPTED, NOW);

        saga.afterLookup(accepted, new ResultLookup.NotReceived());

        verify(account, never()).release(any());
        verify(store).touch(accepted.getId());
    }

    @Test
    void inProgressMovesToAcceptedAndKeepsReservation() {
        when(fraud.check(payment)).thenReturn(new FraudDecision(Verdict.APPROVED, null));
        when(processing.transfer(payment, null)).thenReturn(new TransferResult(TransferStatus.IN_PROGRESS, null, null));

        saga.run(payment, null);

        verify(store).moveWithProcessingId(eq(id), eq(ACCEPTED), eq(null), anyString());
        verify(account, never()).capture(any());
        verify(account, never()).release(any());
    }

    @Test
    void resultNotificationForAcceptedPaymentCaptures() {
        Payment accepted = sentToProcessing();
        accepted.moveTo(ACCEPTED, NOW);
        when(store.get(accepted.getId())).thenReturn(accepted);
        when(store.moveWithProcessingId(any(), any(), any(), anyString())).thenReturn(accepted);
        when(store.move(eq(accepted.getId()), any(), anyString())).thenReturn(accepted);

        saga.onProcessingResult(accepted.getId(), new TransferResult(TransferStatus.COMPLETED, "PRC-7", null));

        verify(store).moveWithProcessingId(eq(accepted.getId()), eq(CONFIRMING), eq("PRC-7"), anyString());
        verify(account).capture(accepted.getId());
    }

    // Повторное или опоздавшее уведомление (платёж уже завершён) — ничего не делаем
    @Test
    void lateNotificationIsIgnored() {
        Payment done = sentToProcessing();
        done.moveTo(CONFIRMING, NOW);
        done.moveTo(SUCCESS, NOW);
        when(store.get(done.getId())).thenReturn(done);

        saga.onProcessingResult(done.getId(), new TransferResult(TransferStatus.DECLINED, null, "поздно"));

        verify(account, never()).release(any());
        verify(store, never()).fail(any(), any(), any(), any());
    }

    private static Payment sentToProcessing() {
        Payment p = new Payment(UUID.randomUUID(), PaymentType.ABROAD, UUID.randomUUID(),
                new Payment.Parties(null, null, null, "JOHN DOE"), null, null, null, new BigDecimal("5000"),
                new BigDecimal("50"), 1, "RUB", UUID.randomUUID(), null, NOW);
        p.moveTo(RESERVED, NOW);
        p.moveTo(FRAUD_APPROVED, NOW);
        p.moveTo(SENT_TO_PROCESSING, NOW);
        return p;
    }
}
