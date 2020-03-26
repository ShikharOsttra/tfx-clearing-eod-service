package com.ihsmarkit.tfx.eod.config;

import static com.ihsmarkit.tfx.core.time.ClockService.JST;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.CASH_BALANCE_UPDATE_BATCH_JOB_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.EOD1_BATCH_JOB_NAME;

import java.util.TimeZone;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.ScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.AllArgsConstructor;

@Configuration
@AllArgsConstructor
public class QuartzConfig {
    private static final String EOD1_JOB_IS_ENABLED_PROPERTY = "eod1.job.enabled";
    private static final String EOD1_JOB_DETAIL_NAME = "eod1JobDetail";
    public static final String EOD1_JOB_TRIGGER1_NAME = "eod1JobTrigger1";
    public static final String EOD1_JOB_TRIGGER2_NAME = "eod1JobTrigger2";
    private static final String BALANCE_UPDATE_JOB_IS_ENABLED_PROPERTY = "cashCollateralBalanceUpdate.job.enabled";
    private static final String BALANCE_UPDATE_JOB_DETAIL_NAME = "cashCollateralBalanceUpdateJobDetail";
    public static final String BALANCE_UPDATE_JOB_TRIGGER_NAME = "cashCollateralBalanceUpdateJobTrigger";

    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone(JST);

    @Value("${eod1.job.trigger1.cron}")
    private final String eod1JobTrigger1Cron;

    @Value("${eod1.job.trigger2.cron}")
    private final String eod1JobTrigger2Cron;

    @Value("${cashCollateralBalanceUpdate.job.trigger.cron}")
    private final String cashCollateralBalanceUpdateJobTriggerCron;

    @Bean(EOD1_JOB_DETAIL_NAME)
    @ConditionalOnProperty(EOD1_JOB_IS_ENABLED_PROPERTY)
    public JobDetail eod1JobDetail() {

        return JobBuilder.newJob(EodQuartzJob.class)
            .withIdentity(EOD1_BATCH_JOB_NAME)
            .storeDurably()
            .build();
    }

    @Bean
    @ConditionalOnBean(name = EOD1_JOB_DETAIL_NAME)
    public Trigger eod1JobTrigger1() {
        final ScheduleBuilder<CronTrigger> scheduleBuilder = CronScheduleBuilder
            .cronSchedule(eod1JobTrigger1Cron)
            .inTimeZone(TIME_ZONE);

        return TriggerBuilder
            .newTrigger()
            .forJob(eod1JobDetail())
            .withIdentity(EOD1_JOB_TRIGGER1_NAME)
            .withSchedule(scheduleBuilder)
            .build();
    }

    @Bean
    @ConditionalOnBean(name = EOD1_JOB_DETAIL_NAME)
    public Trigger eod1JobTrigger2() {
        final ScheduleBuilder<CronTrigger> scheduleBuilder = CronScheduleBuilder
            .cronSchedule(eod1JobTrigger2Cron)
            .inTimeZone(TIME_ZONE);

        return TriggerBuilder
            .newTrigger()
            .forJob(eod1JobDetail())
            .withIdentity(EOD1_JOB_TRIGGER2_NAME)
            .withSchedule(scheduleBuilder)
            .build();
    }

    @Bean(BALANCE_UPDATE_JOB_DETAIL_NAME)
    @ConditionalOnProperty(BALANCE_UPDATE_JOB_IS_ENABLED_PROPERTY)
    public JobDetail cashCollateralBalanceUpdateJobDetail() {

        return JobBuilder.newJob(CashCollateralBalanceUpdateQuartzJob.class)
            .withIdentity(CASH_BALANCE_UPDATE_BATCH_JOB_NAME)
            .storeDurably()
            .build();
    }

    @Bean(BALANCE_UPDATE_JOB_TRIGGER_NAME)
    @ConditionalOnBean(name = BALANCE_UPDATE_JOB_DETAIL_NAME)
    public Trigger cashCollateralBalanceUpdateJobTrigger() {
        final ScheduleBuilder<CronTrigger> scheduleBuilder = CronScheduleBuilder
            .cronSchedule(cashCollateralBalanceUpdateJobTriggerCron)
            .inTimeZone(TIME_ZONE);

        return TriggerBuilder
            .newTrigger()
            .forJob(cashCollateralBalanceUpdateJobDetail())
            .withIdentity(BALANCE_UPDATE_JOB_TRIGGER_NAME)
            .withSchedule(scheduleBuilder)
            .build();
    }
}
