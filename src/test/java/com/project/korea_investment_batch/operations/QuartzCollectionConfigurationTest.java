package com.project.korea_investment_batch.operations;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.DailyCollectionTestSupport;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.KeyMatcher;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.batch.core.repository.JobRepository;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzCollectionConfigurationTest extends DailyCollectionTestSupport {
    private static final JobKey JOB = JobKey.jobKey("dailyCollection", "collection");
    private static final TriggerKey TRIGGER = TriggerKey.triggerKey("dailyCollectionCron", "collection");
    private static final String CRON = "0 30 19 29 2 ?";

    @Test
    void persistsSeoulTriggerAndLaunchesBatchThroughQuartz() throws Exception {
        dailyCollectionFixtures();

        var settings = Map.of("collection.enabled", "true", "collection.cron", CRON);
        Date nextFireTime;
        try (var application = startApplication(settings)) {
            var scheduler = application.getBean(Scheduler.class);
            var trigger = (CronTrigger) scheduler.getTrigger(TRIGGER);
            assertThat(scheduler.getMetaData().isJobStoreSupportsPersistence()).isTrue();
            assertThat(trigger.getTimeZone().toZoneId()).isEqualTo(application.getBean(Clock.class).getZone());
            assertThat(trigger.getNextFireTime().toInstant().atOffset(ZoneOffset.UTC).toLocalTime())
                    .isEqualTo(LocalTime.of(10, 30));
            assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
            assertThat(scheduler.getJobDetail(JOB).isConcurrentExecutionDisallowed()).isTrue();
            assertThat(requests).isEmpty();

            startAndWaitForJob(scheduler);
            var kis = application.getBean(KisModule.class);
            var dart = application.getBean(DartModule.class);
            var date = kis.latest(Market.KOSPI).orElseThrow().collectionDate();
            var execution = kis.execution(run(Market.KOSPI, date)).orElseThrow();
            var parameters = application.getBean(JobRepository.class).getJobExecution(execution.id()).getJobParameters();
            assertThat(parameters.getLocalDate("collectionDate")).isEqualTo(date);
            assertThat(parameters.getLong("inputRevision")).isEqualTo(1L);
            assertThat(dart.scanResult(run(Market.KOSDAQ, date)).orElseThrow().state()).isEqualTo("COMPLETED");
            assertThat(requests).hasSize(9);

            nextFireTime = trigger.getNextFireTime();
            scheduler.pauseTrigger(TRIGGER);
        }

        try (var application = startApplication(Map.of("collection.enabled", "false"))) {
            var scheduler = application.getBean(Scheduler.class);
            assertThat(scheduler.isInStandbyMode()).isTrue();
            assertThat(scheduler.getTrigger(TRIGGER).getNextFireTime()).isEqualTo(nextFireTime);
        }
        try (var application = startApplication(settings)) {
            var scheduler = application.getBean(Scheduler.class);
            assertThat(scheduler.getTriggerState(TRIGGER)).isEqualTo(Trigger.TriggerState.PAUSED);
            assertThat(scheduler.getTrigger(TRIGGER).getNextFireTime()).isEqualTo(nextFireTime);
            assertThat(requests).hasSize(9);
        }
        try (var application = startApplication(Map.of("collection.enabled", "true", "collection.cron", "0 45 19 29 2 ?"))) {
            var scheduler = application.getBean(Scheduler.class);
            var trigger = (CronTrigger) scheduler.getTrigger(TRIGGER);
            assertThat(trigger.getCronExpression()).isEqualTo("0 45 19 29 2 ?");
            assertThat(scheduler.getTriggerState(TRIGGER)).isEqualTo(Trigger.TriggerState.PAUSED);
            scheduler.unscheduleJob(TRIGGER);
        }
    }

    private static void startAndWaitForJob(Scheduler scheduler) throws Exception {
        var result = new CompletableFuture<JobExecutionException>();
        scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
            @Override
            public String getName() {
                return "testDailyCollection";
            }

            @Override
            public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
                result.complete(jobException);
            }
        }, KeyMatcher.keyEquals(JOB));
        scheduler.triggerJob(JOB);
        assertThat(result.get(10, TimeUnit.SECONDS)).isNull();
    }
}
