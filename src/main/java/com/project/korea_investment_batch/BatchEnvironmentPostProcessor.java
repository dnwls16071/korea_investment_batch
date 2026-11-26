package com.project.korea_investment_batch;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

@Order(ConfigDataEnvironmentPostProcessor.ORDER + 1)
public class BatchEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/korea_investment_batch"
            + "?connectionTimeZone=UTC&rewriteBatchedStatements=true";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String runOnStartup = environment.getProperty("COLLECTION_RUN_ON_STARTUP", "false");
        boolean oneShot = environment.containsProperty(CommandLinePropertySource.DEFAULT_NON_OPTION_ARGS_PROPERTY_NAME)
                || Boolean.parseBoolean(environment.getProperty("collection.run-on-startup", runOnStartup));
        String collectionEnabled = environment.getProperty("COLLECTION_ENABLED", Boolean.toString(!oneShot));
        var settings = Map.<String, Object>of(
                "spring.datasource.url", environment.getProperty("BATCH_DB_URL", DEFAULT_DB_URL),
                "spring.datasource.username", environment.getProperty("BATCH_DB_USER", "batch"),
                "spring.datasource.password", environment.getProperty("BATCH_DB_PASSWORD", "batch"),
                "collection.enabled", collectionEnabled,
                "collection.run-on-startup", runOnStartup,
                "collection.cron", environment.getProperty("COLLECTION_CRON", "0 30 19 * * ?"),
                "spring.main.keep-alive", environment.getProperty("collection.enabled", collectionEnabled)
        );
        environment.getPropertySources().addLast(new MapPropertySource("batchRuntimeSettings", settings));
    }
}
