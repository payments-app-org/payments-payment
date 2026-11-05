package com.paymentsapp.payment.client;

// Ответ на вопрос «чем закончился перевод?» у процессинга или поставщика. Три исхода, и путать их нельзя:
// «не получали» — деньги точно не ушли, резерв можно снять; «не удалось узнать» (сосед лежит, тайм-аут, 5xx) —
// деньги могли уйти, возвращать их нельзя, спрашиваем позже.
public sealed interface ResultLookup {

    record Found(ProcessingClient.TransferResult result) implements ResultLookup {
    }

    record NotReceived() implements ResultLookup {
    }

    record Unknown(String reason) implements ResultLookup {
    }
}
