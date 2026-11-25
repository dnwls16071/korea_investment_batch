package com.project.korea_investment_batch.shared;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock collectionClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
