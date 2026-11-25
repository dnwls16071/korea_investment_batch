package com.project.korea_investment_batch.operations;

import java.util.Arrays;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.ValidateOutput;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class QuartzSchemaMigrationConfiguration {
    private static final MigrationVersion QUARTZ_SCHEMA_VERSION = MigrationVersion.fromVersion("005");
    private static final String QUARTZ_SCHEMA_SCRIPT = "V005__quartz_job_store.sql";
    private static final int DISPLAY_WIDTH_CHECKSUM = 1033021684;
    private static final int NORMALIZED_CHECKSUM = 1536720656;

    @Bean
    FlywayMigrationStrategy quartzSchemaMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayValidateException failure) {
                if (!hasOnlyLegacyQuartzMismatch(flyway)) {
                    throw failure;
                }
                flyway.repair();
                flyway.migrate();
            }
        };
    }

    private static boolean hasOnlyLegacyQuartzMismatch(Flyway flyway) {
        var applied = Arrays.asList(flyway.info().applied());
        // Flyway repair can change other history entries, so check every applied migration first.
        return applied.stream().filter(QuartzSchemaMigrationConfiguration::isLegacyQuartzMigration).count() == 1
                && applied.stream().allMatch(migration -> migration.getState().isResolved()
                        && !migration.getState().isFailed()
                        && (Objects.equals(migration.getAppliedChecksum(), migration.getResolvedChecksum())
                            || isLegacyQuartzMigration(migration)))
                && flyway.validateWithResult().invalidMigrations.stream()
                        .allMatch(QuartzSchemaMigrationConfiguration::canRepairWithValidationIssue);
    }

    private static boolean canRepairWithValidationIssue(ValidateOutput migration) {
        return migration.errorDetails.errorCode == CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED
                || migration.errorDetails.errorCode == CoreErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED
                || (migration.errorDetails.errorCode == CoreErrorCode.CHECKSUM_MISMATCH
                    && QUARTZ_SCHEMA_VERSION.getVersion().equals(migration.version));
    }

    private static boolean isLegacyQuartzMigration(MigrationInfo migration) {
        return QUARTZ_SCHEMA_VERSION.equals(migration.getVersion())
                && QUARTZ_SCHEMA_SCRIPT.equals(migration.getScript())
                && migration.getType() == CoreMigrationType.SQL
                && "quartz job store".equals(migration.getDescription())
                && Objects.equals(migration.getAppliedChecksum(), DISPLAY_WIDTH_CHECKSUM)
                && Objects.equals(migration.getResolvedChecksum(), NORMALIZED_CHECKSUM);
    }
}
