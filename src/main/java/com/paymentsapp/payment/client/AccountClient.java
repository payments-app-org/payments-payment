package com.paymentsapp.payment.client;

import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.platform.error.RemoteProblem;
import com.paymentsapp.platform.resilience.ResilientCalls;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

// Резерв, списание и снятие резерва в account. Все вызовы идемпотентны по paymentId — их можно повторять.
// Бизнес-отказ (4xx) → AccountRejectedException, сбой сети, 5xx или разомкнутый circuit breaker → RestClientException.
@Component
public class AccountClient {

    private static final String DEFAULT_REJECTION = "Отказ сервиса счетов";
    private static final String LAST4_SEPARATOR = " ·· ";

    private final AccountApi api;
    private final ResilientCalls calls;
    private final JsonMapper json;

    AccountClient(AccountApi api, ResilientCalls calls, JsonMapper json) {
        this.api = api;
        this.calls = calls;
        this.json = json;
    }

    public void reserve(Payment p) {
        AccountApi.ReserveRequest request = new AccountApi.ReserveRequest(p.getId(), p.getUserId(), p.getSourceAccountId(),
                p.getRecipientAccountId(), p.getType().name(), p.getAmount(),
                p.getCommissionAmount().add(p.getRoundUp().amount()), p.getCurrency(),
                p.getBill() == null ? null : p.getBill().providerGroup());
        calls.run(Backends.ACCOUNT, () -> {
            try {
                api.reserve(request);
            } catch (HttpClientErrorException e) {
                RemoteProblem problem = RemoteProblem.of(e, json, DEFAULT_REJECTION);
                throw new AccountRejectedException(problem.code(), problem.detail());
            }
        });
    }

    public void capture(UUID paymentId) {
        calls.run(Backends.ACCOUNT, () -> api.capture(paymentId));
    }

    // Резерва нет (резервирование так и не дошло до account) — снимать нечего, это тоже успех
    public void release(UUID paymentId) {
        calls.run(Backends.ACCOUNT, () -> {
            try {
                api.release(paymentId);
            } catch (HttpClientErrorException.NotFound e) {
                // резерва нет
            }
        });
    }

    // Наш ли клиент получатель. Пусто — реквизиты не наши, перевод уйдёт в другой банк.
    public Optional<Recipient> findRecipientByPhone(String phone) {
        return calls.call(Backends.ACCOUNT, () -> notFoundAsEmpty(() -> api.recipientByPhone(phone)));
    }

    public Optional<Recipient> findRecipientByCard(String cardNumber) {
        return calls.call(Backends.ACCOUNT, () -> notFoundAsEmpty(() -> api.recipientByCard(cardNumber)));
    }

    // Профиль: имя отправителя (как его увидит получатель), счета с названиями и типами, «Округление на пользу»
    public UserInfo user(UUID userId) {
        AccountApi.UserProfile profile = calls.call(Backends.ACCOUNT, () -> api.user(userId));
        Map<UUID, AccountInfo> accounts = profile.accounts().stream().collect(Collectors.toMap(AccountApi.AccountView::id,
                a -> new AccountInfo(a.cardLast4() == null ? a.title() : a.title() + LAST4_SEPARATOR + a.cardLast4(), a.type())));
        AccountApi.RoundupView r = profile.roundup();
        return new UserInfo(profile.displayName(), accounts, r == null ? null : new Roundup(r.step(), r.fundCode(), r.fundName()));
    }

    private static <T> Optional<T> notFoundAsEmpty(Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    // «Округление на пользу»: шаг и фонд
    public record Roundup(int step, String fundCode, String fundName) {
    }

    public record UserInfo(String displayName, Map<UUID, AccountInfo> accounts, Roundup roundup) {

        public String title(UUID accountId, String fallback) {
            AccountInfo a = accounts.get(accountId);
            return a == null ? fallback : a.title();
        }

        public boolean isExternal(UUID accountId) {
            AccountInfo a = accounts.get(accountId);
            return a != null && a.kind() != null && a.kind().isExternal();
        }
    }

    public record AccountInfo(String title, AccountKind kind) {
    }

    // walletAccountId — «Мой кошелёк» получателя (только при поиске по телефону)
    public record Recipient(UUID userId, String displayName, UUID accountId, String sbpBankId, UUID walletAccountId) {
    }
}
