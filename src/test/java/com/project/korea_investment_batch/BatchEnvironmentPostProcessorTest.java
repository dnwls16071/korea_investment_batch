package com.project.korea_investment_batch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class BatchEnvironmentPostProcessorTest {

    @Test
    void enablesSchedulingAndKeepsTheProcessAliveByDefault() {
        assertScheduling(new MockEnvironment(), true);
    }

    @Test
    void letsAnOperationsCommandExitAfterItRuns() {
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource("kis", "latest", "KOSPI"));

        assertScheduling(environment, false);
    }

    @Test
    void letsAnImmediateCollectionExitAfterItRuns() {
        assertScheduling(new MockEnvironment().withProperty("COLLECTION_RUN_ON_STARTUP", "true"), false);
    }

    @Test
    void honorsExplicitScheduleSettings() {
        assertScheduling(new MockEnvironment().withProperty("COLLECTION_ENABLED", "false"), false);
        assertScheduling(new MockEnvironment().withProperty("COLLECTION_RUN_ON_STARTUP", "true")
                .withProperty("COLLECTION_ENABLED", "true"), true);
    }

    private void assertScheduling(MockEnvironment environment, boolean enabled) {
        new BatchEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("collection.enabled", Boolean.class)).isEqualTo(enabled);
        assertThat(environment.getProperty("spring.main.keep-alive", Boolean.class)).isEqualTo(enabled);
    }
}
