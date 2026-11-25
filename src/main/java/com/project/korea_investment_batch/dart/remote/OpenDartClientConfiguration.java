package com.project.korea_investment_batch.dart.remote;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenDartClientConfiguration {
    @Bean
    URI openDartEndpoint() {
        return URI.create("https://opendart.fss.or.kr/api/list.json");
    }
}
