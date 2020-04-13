package com.ihsmarkit.tfx.eod.config;

import static com.ihsmarkit.tfx.eod.config.EodJobConstants.EOD1_BATCH_JOB_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.MTM_TRADES_STEP_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.NET_TRADES_STEP_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.REBALANCE_POSITIONS_STEP_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.ROLL_POSITIONS_STEP_NAME;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.listener.StepExecutionListenerSupport;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ihsmarkit.tfx.alert.client.domain.Eod1CompletedAlert;
import com.ihsmarkit.tfx.alert.client.domain.Eod1StartAlert;
import com.ihsmarkit.tfx.eod.batch.MarkToMarketTradesTasklet;
import com.ihsmarkit.tfx.eod.batch.NettingTasklet;
import com.ihsmarkit.tfx.eod.batch.PositionRollTasklet;
import com.ihsmarkit.tfx.eod.batch.RebalancingTasklet;
import com.ihsmarkit.tfx.eod.config.listeners.EodFailedStepAlertSender;
import com.ihsmarkit.tfx.eod.config.listeners.EodJobListenerFactory;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Configuration
public class EOD1JobConfig {

    private final JobBuilderFactory jobs;

    private final StepBuilderFactory steps;

    private final MarkToMarketTradesTasklet markToMarketTradesTasklet;

    private final NettingTasklet nettingTasklet;

    private final RebalancingTasklet rebalancingTasklet;

    private final PositionRollTasklet positionRollTasklet;

    private final EodJobListenerFactory eodJobListenerFactory;

    private final EodFailedStepAlertSender eodFailedStepAlertSender;

    @Bean(name = EOD1_BATCH_JOB_NAME)
    public Job eod1Job() {
        return jobs.get(EOD1_BATCH_JOB_NAME)
            .listener(eodJobListenerFactory.listener(Eod1StartAlert::of, Eod1CompletedAlert::of))
            .start(mtmTrades())
            .next(netTrades())
            .next(rebalancePositions())
            .next(rollPositions())
            .build();
    }

    private Step mtmTrades() {
        return createStep(MTM_TRADES_STEP_NAME, markToMarketTradesTasklet, eodFailedStepAlertSender.mtmFailedListener());
    }

    private Step netTrades() {
        return createStep(NET_TRADES_STEP_NAME, nettingTasklet, eodFailedStepAlertSender.nettingFailedListener());
    }

    private Step rebalancePositions() {
        return createStep(REBALANCE_POSITIONS_STEP_NAME, rebalancingTasklet, new StepExecutionListenerSupport());
    }

    private Step rollPositions() {
        return createStep(ROLL_POSITIONS_STEP_NAME, positionRollTasklet, new StepExecutionListenerSupport());
    }

    private Step createStep(final String stepName, final Tasklet stepTasklet, final StepExecutionListener stepExecutionListener) {
        return steps.get(stepName)
            .listener(stepExecutionListener)
            .tasklet(stepTasklet)
            .build();
    }
}
