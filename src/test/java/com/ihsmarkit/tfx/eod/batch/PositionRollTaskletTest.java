package com.ihsmarkit.tfx.eod.batch;

import static com.ihsmarkit.tfx.core.dl.EntityTestDataFactory.aParticipantEntityBuilder;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.BUSINESS_DATE_FMT;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.BUSINESS_DATE_JOB_PARAM_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.ROLL_POSITIONS_STEP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import com.ihsmarkit.tfx.core.dl.entity.CurrencyPairEntity;
import com.ihsmarkit.tfx.core.dl.entity.ParticipantEntity;
import com.ihsmarkit.tfx.core.dl.entity.eod.ParticipantPositionEntity;
import com.ihsmarkit.tfx.core.dl.repository.eod.ParticipantPositionRepository;
import com.ihsmarkit.tfx.core.domain.type.ParticipantPositionType;
import com.ihsmarkit.tfx.eod.mapper.TradeOrPositionEssentialsMapper;
import com.ihsmarkit.tfx.eod.model.ParticipantCurrencyPairAmount;
import com.ihsmarkit.tfx.eod.service.DailySettlementPriceProvider;
import com.ihsmarkit.tfx.eod.service.EODCalculator;
import com.ihsmarkit.tfx.eod.service.TradeAndSettlementDateService;

class PositionRollTaskletTest extends AbstractSpringBatchTest {

    private static final String BUSINESS_DATE_STR = "20191006";
    private static final LocalDate BUSINESS_DATE = LocalDate.parse(BUSINESS_DATE_STR, BUSINESS_DATE_FMT);
    private static final LocalDate NEXT_DATE = LocalDate.of(2019, 10, 8);
    private static final LocalDate VALUE_DATE = LocalDate.of(2019, 10, 10);

    private static final CurrencyPairEntity EURUSD = CurrencyPairEntity.of(2L, "EUR", "USD");
    private static final BigDecimal EURUSD_RATE = BigDecimal.valueOf(1.1);

    private static final ParticipantEntity PARTICIPANT_A = aParticipantEntityBuilder().name("A").build();

    @MockBean
    private ParticipantPositionRepository participantPositionRepository;

    @MockBean
    private DailySettlementPriceProvider dailySettlementPriceProvider;

    @MockBean
    private TradeAndSettlementDateService tradeAndSettlementDateService;

    @MockBean
    private EODCalculator eodCalculator;

    @Captor
    private ArgumentCaptor<Iterable<ParticipantPositionEntity>> positionCaptor;

    @Test
    void shouldRollPositions() {

        Stream<ParticipantPositionEntity> positions = Stream.empty();

        when(dailySettlementPriceProvider.getDailySettlementPrices(BUSINESS_DATE))
            .thenReturn(Map.of(EURUSD, EURUSD_RATE));

        when(participantPositionRepository.findAllNetAndRebalancingPositionsByTradeDate(any())).thenReturn(positions);

        when(eodCalculator.aggregatePositions(any())).thenReturn(
            Stream.of(ParticipantCurrencyPairAmount.of(PARTICIPANT_A, EURUSD, BigDecimal.TEN))
        );

        when(tradeAndSettlementDateService.getNextTradeDate(any(), any())).thenReturn(NEXT_DATE);
        when(tradeAndSettlementDateService.getValueDate(any(), any())).thenReturn(VALUE_DATE);

        final JobExecution execution = jobLauncherTestUtils.launchStep(ROLL_POSITIONS_STEP_NAME,
            new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters())
                .addString(BUSINESS_DATE_JOB_PARAM_NAME, BUSINESS_DATE_STR)
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        verify(eodCalculator).aggregatePositions(positions);

        verify(participantPositionRepository).findAllNetAndRebalancingPositionsByTradeDate(BUSINESS_DATE);

        verify(participantPositionRepository).saveAll(positionCaptor.capture());
        assertThat(positionCaptor.getValue())
            .extracting(
                ParticipantPositionEntity::getCurrencyPair,
                ParticipantPositionEntity::getParticipant,
                position -> position.getAmount().getValue(),
                ParticipantPositionEntity::getPrice,
                ParticipantPositionEntity::getTradeDate,
                ParticipantPositionEntity::getValueDate,
                ParticipantPositionEntity::getType
            ).containsOnly(
                tuple(EURUSD, PARTICIPANT_A, BigDecimal.TEN, EURUSD_RATE, NEXT_DATE, VALUE_DATE, ParticipantPositionType.SOD)
            );

        verifyNoMoreInteractions(eodCalculator, participantPositionRepository);

    }

    @TestConfiguration
    @ComponentScan(basePackageClasses = {
        RebalancingTasklet.class, TradeOrPositionEssentialsMapper.class, ParticipantPositionRepository.class,
        EODCalculator.class
    },
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                PositionRollTasklet.class,
                EODCalculator.class,
                TradeOrPositionEssentialsMapper.class,
                ParticipantPositionRepository.class
            })
    )
    static class TestConfig {

    }
}