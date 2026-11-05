package com.paymentsapp.payment.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RuRequisitesValidatorTest {

    private static final String OUR_BIK = "044525999";
    private final RuRequisitesValidator validator = new RuRequisitesValidator(new RequisitesProperties(OUR_BIK),
            JsonMapper.builder().build());

    private Map<String, String> fields() {
        Map<String, String> f = new HashMap<>();
        f.put("account", "40702810938000012345");
        f.put("bik", "044525225");
        f.put("inn", "7707123458");
        f.put("kpp", "770701001");
        f.put("name", "ООО Ромашка");
        f.put("purpose", "Оплата по счёту 15");
        f.put("payerName", "Руднев Артур");
        return f;
    }

    @Test
    void validRequisitesOfOrganization() {
        RuRequisitesValidator.Checked checked = validator.check(fields(), false);
        assertThat(checked.recipientName()).isEqualTo("ООО Ромашка");
        assertThat(checked.json()).contains("\"kpp\":\"770701001\"");
    }

    @Test
    void controlNumbers() {
        assertThat(RuRequisitesValidator.innValid("7707123458")).isTrue();
        assertThat(RuRequisitesValidator.innValid("7707123459")).isFalse();
        assertThat(RuRequisitesValidator.innValid("771234567859")).isTrue();
        assertThat(RuRequisitesValidator.accountKeyValid("044525225", "40702810938000012345")).isTrue();
    }

    @Test
    void accountNotMatchingBikAndOurBikInOtherBank() {
        Map<String, String> wrongAccount = fields();
        wrongAccount.put("account", "40702810938000012346");
        assertThatThrownBy(() -> validator.check(wrongAccount, false)).hasMessageContaining("не сходится с БИК");

        Map<String, String> ourBik = fields();
        ourBik.put("bik", OUR_BIK);
        assertThatThrownBy(() -> validator.check(ourBik, false)).hasMessageContaining("БИК нашего банка");
    }
}
