package com.project.korea_investment_batch.operations;

import java.time.Clock;
import java.util.Properties;
import java.util.TimeZone;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class QuartzCollectionConfiguration {

    // Quartz uses the scheduler name to locate its persisted jobs and triggers.
    private static final String SCHEDULER_NAME = "koreaInvestmentCollection";
    private static final JobKey DAILY_JOB = JobKey.jobKey("dailyCollection", "collection");
    private static final TriggerKey DAILY_TRIGGER = TriggerKey.triggerKey("dailyCollectionCron", "collection");

    @Bean
    SchedulerFactoryBeanCustomizer collectionSchedulerCustomizer(QuartzProperties bootProperties) {
        return factory -> {
            factory.setSchedulerName(SCHEDULER_NAME);
            factory.setAutoStartup(false);
            factory.setWaitForJobsToCompleteOnShutdown(true);

            // A one-shot collection command must be able to exit while Quartz is in standby.
            var properties = new Properties();
            properties.putAll(bootProperties.getProperties());
            properties.setProperty("org.quartz.scheduler.makeSchedulerThreadDaemon", "true");
            properties.setProperty("org.quartz.threadPool.makeThreadsDaemons", "true");
            factory.setQuartzProperties(properties);
        };
    }

    @Bean
    JobDetail dailyCollectionJob() {
        return JobBuilder.newJob(DailyCollectionQuartzJob.class).withIdentity(DAILY_JOB).storeDurably().build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "collection", name = "enabled", havingValue = "true")
    ApplicationRunner startCollectionScheduler(
            Scheduler scheduler,
            PlatformTransactionManager transactionManager,
            @Qualifier("collectionClock") Clock clock,
            Environment environment
    ) {
        String cron = environment.getRequiredProperty("collection.cron");
        var schedule = CronScheduleBuilder.cronSchedule(cron)
                .inTimeZone(TimeZone.getTimeZone(clock.getZone()))
                .withMisfireHandlingInstructionDoNothing();
        var trigger = TriggerBuilder.newTrigger()
                .withIdentity(DAILY_TRIGGER)
                .forJob(DAILY_JOB)
                .withSchedule(schedule)
                .build();
        var transaction = new TransactionTemplate(transactionManager);
        return arguments -> {
            transaction.executeWithoutResult(status -> {
                try {
                    registerTrigger(scheduler, trigger);
                } catch (SchedulerException e) {
                    throw new IllegalStateException("Could not register the daily Quartz trigger", e);
                }
            });
            scheduler.start();
        };
    }

    private void registerTrigger(Scheduler scheduler, CronTrigger trigger) throws SchedulerException {
        var existing = scheduler.getTrigger(DAILY_TRIGGER);
        if (existing == null) {
            scheduler.scheduleJob(trigger);
        } else if (!(existing instanceof CronTrigger cron
                && cron.getCronExpression().equals(trigger.getCronExpression())
                && cron.getTimeZone().equals(trigger.getTimeZone())
                && cron.getMisfireInstruction() == trigger.getMisfireInstruction())) {
            boolean paused = scheduler.getTriggerState(DAILY_TRIGGER) == Trigger.TriggerState.PAUSED;
            scheduler.rescheduleJob(DAILY_TRIGGER, trigger);
            if (paused) {
                scheduler.pauseTrigger(DAILY_TRIGGER);
            }
        }
    }
}
