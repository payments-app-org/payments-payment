package com.paymentsapp.payment.error;

import com.paymentsapp.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

// Отказы payment до создания платежа. code — что видит фронтенд; у ошибок проверки реквизитов общий INVALID_REQUISITES.
public enum PaymentError implements ErrorCode {

    INVALID_QUOTE(HttpStatus.BAD_REQUEST, "Некорректная котировка"),
    QUOTE_SIGNATURE(HttpStatus.BAD_REQUEST, "Подпись котировки не сходится", Codes.INVALID_QUOTE),
    QUOTE_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Расчёт комиссии устарел, обновите сумму"),
    QUOTE_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Котировка выдана на другой платёж"),
    FX_QUOTE_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Курс пересчитан на другую сумму, обновите расчёт", Codes.QUOTE_MISMATCH),
    QUOTE_ALREADY_USED(HttpStatus.CONFLICT, "Эта котировка уже использована, пересчитайте комиссию"),

    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Минимальная сумма — %s"),

    PHONE_FORMAT(HttpStatus.BAD_REQUEST, "Телефон получателя в формате +7XXXXXXXXXX", Codes.INVALID_REQUISITES),
    BANK_FORMAT(HttpStatus.BAD_REQUEST, "Некорректный банк получателя", Codes.INVALID_REQUISITES),
    CARD_NOT_NEEDED(HttpStatus.BAD_REQUEST, "Для перевода по телефону номер карты не нужен", Codes.INVALID_REQUISITES),
    CARD_FORMAT(HttpStatus.BAD_REQUEST, "Некорректный номер карты получателя", Codes.INVALID_REQUISITES),
    PHONE_NOT_NEEDED(HttpStatus.BAD_REQUEST, "Для перевода по карте телефон и банк не нужны", Codes.INVALID_REQUISITES),
    ABROAD_PARAMS_REQUIRED(HttpStatus.BAD_REQUEST, "Не выбраны страна, валюта или способ перевода", Codes.INVALID_REQUISITES),
    ABROAD_FIELDS_ONLY(HttpStatus.BAD_REQUEST, "Данные получателя передаются в recipientFields", Codes.INVALID_REQUISITES),
    REQUISITES_REQUIRED(HttpStatus.BAD_REQUEST, "Заполните реквизиты получателя", Codes.INVALID_REQUISITES),
    PROVIDER_REQUIRED(HttpStatus.BAD_REQUEST, "Не выбран поставщик", Codes.INVALID_REQUISITES),
    DESTINATION_REQUIRED(HttpStatus.BAD_REQUEST, "Не выбран счёт зачисления", Codes.INVALID_REQUISITES),
    SAME_ACCOUNT(HttpStatus.BAD_REQUEST, "Нельзя перевести на тот же счёт", Codes.INVALID_REQUISITES),
    OWN_REQUISITES_NOT_NEEDED(HttpStatus.BAD_REQUEST, "Для перевода между своими счетами реквизиты не нужны", Codes.INVALID_REQUISITES),

    BIK_FORMAT(HttpStatus.BAD_REQUEST, "БИК — 9 цифр, начинается с 04", Codes.INVALID_REQUISITES),
    OUR_BIK(HttpStatus.BAD_REQUEST, "Это БИК нашего банка — выберите «Счета нашего банка»", Codes.INVALID_REQUISITES),
    ACCOUNT_KEY(HttpStatus.BAD_REQUEST, "Проверьте номер счёта получателя: не сходится с БИК", Codes.INVALID_REQUISITES),
    RECIPIENT_INN(HttpStatus.BAD_REQUEST, "Проверьте ИНН получателя", Codes.INVALID_REQUISITES),
    RECIPIENT_NAME(HttpStatus.BAD_REQUEST, "Укажите название компании получателя", Codes.INVALID_REQUISITES),
    KPP_FORMAT(HttpStatus.BAD_REQUEST, "КПП — 9 символов (у ИП не указывается)", Codes.INVALID_REQUISITES),
    PURPOSE_REQUIRED(HttpStatus.BAD_REQUEST, "Укажите назначение платежа (до %d символов)", Codes.INVALID_REQUISITES),
    PAYER_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "Укажите ФИО отправителя", Codes.INVALID_REQUISITES),
    PAYER_INN(HttpStatus.BAD_REQUEST, "Проверьте ИНН отправителя", Codes.INVALID_REQUISITES),

    METHOD_NOT_FOUND(HttpStatus.BAD_REQUEST, "Способ перевода не найден", Codes.INVALID_REQUISITES),
    METHOD_COUNTRY(HttpStatus.BAD_REQUEST, "Способ перевода недоступен для этой страны", Codes.INVALID_REQUISITES),
    METHOD_AMOUNT_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "Этим способом можно отправить от %s до %s"),
    CURRENCY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "В эту страну нельзя перевести в валюте %s", Codes.INVALID_REQUISITES),
    FIELD_REQUIRED(HttpStatus.BAD_REQUEST, "Заполните поле «%s»", Codes.INVALID_REQUISITES),
    FIELD_INVALID(HttpStatus.BAD_REQUEST, "Проверьте поле «%s»", Codes.INVALID_REQUISITES),
    // Текст отказа biller передаём как есть
    BILLER_REJECTED(HttpStatus.BAD_REQUEST, "Проверьте данные платежа", Codes.INVALID_REQUISITES),

    RECIPIENT_BANK_UNKNOWN(HttpStatus.UNPROCESSABLE_CONTENT, "Не удалось определить банк получателя, выберите банк из списка"),
    RECIPIENT_BANK_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "У получателя нет счёта в этом банке"),
    WALLET_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "Кошелёк с таким номером не найден", Codes.RECIPIENT_NOT_FOUND),
    OWN_WALLET(HttpStatus.UNPROCESSABLE_CONTENT, "Это ваш кошелёк — пополните его переводом между своими счетами",
            Codes.RECIPIENT_NOT_FOUND),
    BILL_NOT_PAYABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Это начисление уже оплачено или оплачивается"),

    ACCOUNT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Сервис счетов недоступен, попробуйте позже", Codes.SERVICE_UNAVAILABLE),
    SBP_DIRECTORY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Не удалось проверить банк получателя, попробуйте позже",
            Codes.SERVICE_UNAVAILABLE),
    METHODS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Справочник переводов недоступен, попробуйте позже", Codes.SERVICE_UNAVAILABLE),
    BILLER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Справочник поставщиков недоступен, попробуйте позже", Codes.SERVICE_UNAVAILABLE),

    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "Ключ идемпотентности уже использован для другого запроса"),
    PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "Запрос с этим ключом ещё обрабатывается"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Платёж не найден"),

    // Внутренний API для процессинга
    INCOMING_RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Получатель с таким телефоном не найден", Codes.RECIPIENT_NOT_FOUND),
    RESULT_RETRY_LATER(HttpStatus.SERVICE_UNAVAILABLE, "Платёж сейчас обрабатывается, повторите запрос позже",
            Codes.SERVICE_UNAVAILABLE);

    private final HttpStatus status;
    private final String template;
    private final String code;

    PaymentError(HttpStatus status, String template) {
        this(status, template, null);
    }

    PaymentError(HttpStatus status, String template, String code) {
        this.status = status;
        this.template = template;
        this.code = code;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String template() {
        return template;
    }

    @Override
    public String code() {
        return code != null ? code : name();
    }

    // Временный сбой соседа: ключ идемпотентности освобождаем, повтор с тем же ключом пройдёт заново
    public boolean isTransient() {
        return status == HttpStatus.SERVICE_UNAVAILABLE;
    }

    private static final class Codes {
        static final String INVALID_QUOTE = "INVALID_QUOTE";
        static final String QUOTE_MISMATCH = "QUOTE_MISMATCH";
        static final String INVALID_REQUISITES = "INVALID_REQUISITES";
        static final String RECIPIENT_NOT_FOUND = "RECIPIENT_NOT_FOUND";
        static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    }
}
