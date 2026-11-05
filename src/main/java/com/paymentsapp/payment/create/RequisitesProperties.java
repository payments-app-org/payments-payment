package com.paymentsapp.payment.create;

import org.springframework.boot.context.properties.ConfigurationProperties;

// ourBik — БИК нашего банка: «Юрлицу в нашем банке» подставляет его, «в другой банк» с ним не принимает
@ConfigurationProperties("payment.requisites")
public record RequisitesProperties(String ourBik) {
}
