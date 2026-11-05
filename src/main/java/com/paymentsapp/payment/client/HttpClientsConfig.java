package com.paymentsapp.payment.client;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

// HTTP-клиенты соседей — интерфейсы @HttpExchange. Адрес и тайм-ауты группы — spring.http.serviceclient.<группа>.*
@Configuration
@ImportHttpServices(group = Backends.ACCOUNT, types = AccountApi.class)
@ImportHttpServices(group = Backends.FRAUD, types = FraudApi.class)
@ImportHttpServices(group = Backends.PROCESSING, types = ProcessingApi.class)
@ImportHttpServices(group = Backends.BILLER, types = BillerApi.class)
class HttpClientsConfig {
}
