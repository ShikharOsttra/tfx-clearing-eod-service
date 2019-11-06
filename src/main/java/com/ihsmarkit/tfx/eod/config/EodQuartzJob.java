package com.ihsmarkit.tfx.eod.config;

import static com.ihsmarkit.tfx.eod.config.EodJobConstants.BUSINESS_DATE_FMT;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.BUSINESS_DATE_JOB_PARAM_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.CURRENT_TSP_JOB_PARAM_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.JOB_NAME_PARAM_NAME;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.quartz.JobExecutionContext;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.ihsmarkit.tfx.core.time.ClockService;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressFBWarnings("NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR")
@RequiredArgsConstructor
public class EodQuartzJob extends QuartzJobBean {

    private final JobLauncher jobLauncher;

    private final JobLocator jobLocator;

    private final ClockService clockService;

    @Override
    @SneakyThrows
    protected void executeInternal(final JobExecutionContext context) {
        final String jobName = context.getJobDetail().getJobDataMap().getString(JOB_NAME_PARAM_NAME);
        final Job job = jobLocator.getJob(jobName);
        final JobParameters params = new JobParametersBuilder()
            .addString(BUSINESS_DATE_JOB_PARAM_NAME, getBusinessDate().format(BUSINESS_DATE_FMT))
            .addString(CURRENT_TSP_JOB_PARAM_NAME, LocalDateTime.now().toString())
            .toJobParameters();

        jobLauncher.run(job, params);
    }

    private LocalDate getBusinessDate() {
        return clockService.getCurrentDate().minus(1, ChronoUnit.DAYS);
    }
}