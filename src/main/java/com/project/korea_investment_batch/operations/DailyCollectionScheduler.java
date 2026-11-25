package com.project.korea_investment_batch.operations;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.kis.KisModule;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyCollectionScheduler {
    private final KisModule kis;
    private final DartModule dart;
    @Qualifier("collectionClock")
    private final Clock clock;

    public void collect() {
        collect(LocalDate.now(clock));
    }

    public void collect(LocalDate date) {
        log.info("KIS daily result for {}: {}", date, kis.collect(date));
        dart.collect(date);
    }

    @Bean
    @ConditionalOnProperty(prefix = "collection", name = "run-on-startup", havingValue = "true")
    ApplicationRunner collectOnStartup() {
        return arguments -> collect();
    }
}
