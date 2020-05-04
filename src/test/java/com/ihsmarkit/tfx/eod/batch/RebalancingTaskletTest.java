package com.ihsmarkit.tfx.eod.batch;

import static com.ihsmarkit.tfx.core.dl.EntityTestDataFactory.aParticipantEntityBuilder;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.BUSINESS_DATE_FMT;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.BUSINESS_DATE_JOB_PARAM_NAME;
import static com.ihsmarkit.tfx.eod.config.EodJobConstants.REBALANCE_POSITIONS_STEP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import com.ihsmarkit.tfx.core.dl.entity.CurrencyPairEntity;
import com.ihsmarkit.tfx.core.dl.entity.ParticipantEntity;
import com.ihsmarkit.tfx.core.dl.entity.eod.ParticipantPositionEntity;
import com.ihsmarkit.tfx.core.dl.repository.TradeRepository;
import com.ihsmarkit.tfx.core.dl.repository.eod.ParticipantPositionRepository;
import com.ihsmarkit.tfx.core.domain.Amount;
import com.ihsmarkit.tfx.core.domain.CurrencyPair;
import com.ihsmarkit.tfx.core.domain.transaction.NewTransaction;
import com.ihsmarkit.tfx.core.domain.transaction.NewTransactionsRequest;
import com.ihsmarkit.tfx.core.domain.type.ParticipantPositionType;
import com.ihsmarkit.tfx.eod.config.AbstractSpringBatchTest;
import com.ihsmarkit.tfx.eod.config.EOD1JobConfig;
import com.ihsmarkit.tfx.eod.model.BalanceTrade;
import com.ihsmarkit.tfx.eod.model.CcyParticipantAmount;
import com.ihsmarkit.tfx.eod.model.ParticipantCurrencyPairAmount;
import com.ihsmarkit.tfx.eod.service.DailySettlementPriceService;
import com.ihsmarkit.tfx.eod.service.EODCalculator;
import com.ihsmarkit.tfx.eod.service.TradeAndSettlementDateService;
import com.ihsmarkit.tfx.eod.service.TransactionsSender;
import com.ihsmarkit.tfx.mailing.client.AwsSesMailClient;

@ContextConfiguration(classes = EOD1JobConfig.class)
class RebalancingTaskletTest extends AbstractSpringBatchTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2019, 10, 6);
    private static final LocalDate VALUE_DATE = BUSINESS_DATE.plusDays(2);

    private static final CurrencyPairEntity EURUSD = CurrencyPairEntity.of(2L, "EUR", "USD");
    private static final BigDecimal EURUSD_RATE = BigDecimal.valueOf(1.1);

    private static final ParticipantEntity PARTICIPANT_A = aParticipantEntityBuilder().code("A").build();
    private static final ParticipantEntity PARTICIPANT_B = aParticipantEntityBuilder().code("B").build();
    private static final ParticipantEntity PARTICIPANT_C = aParticipantEntityBuilder().code("C").build();
    private static final ParticipantEntity PARTICIPANT_D = aParticipantEntityBuilder().code("D").build();

    @MockBean
    private TradeRepository tradeRepository;

    @MockBean
    private ParticipantPositionRepository participantPositionRepository;

    @MockBean
    private DailySettlementPriceService dailySettlementPriceService;

    @MockBean
    private EODCalculator eodCalculator;

    @MockBean
    private TradeAndSettlementDateService tradeAndSettlementDateService;

    @MockBean
    private AwsSesMailClient mailClient;

    @Autowired
    private TransactionsSender transactionsSender;

    @Captor
    private ArgumentCaptor<Iterable<ParticipantPositionEntity>> positionCaptor;

    @Captor
    private ArgumentCaptor<NewTransactionsRequest> transactionCaptor;

    @Captor
    private ArgumentCaptor<Stream<CcyParticipantAmount>> netCaptor;

    @Test
    void shouldRebalanceTrades() {

        Stream<ParticipantPositionEntity> positions = Stream.empty();

        when(tradeAndSettlementDateService.getValueDate(BUSINESS_DATE, EURUSD)).thenReturn(VALUE_DATE);

        when(dailySettlementPriceService.getPrice(BUSINESS_DATE, EURUSD)).thenReturn(EURUSD_RATE);

        when(participantPositionRepository.findAllNetPositionsOfActiveLPByTradeDateFetchParticipant(any())).thenReturn(positions);

        when(eodCalculator.rebalanceLPPositions(any(), any())).thenReturn(
            Collections.singletonMap(
                EURUSD,
                List.of(
                    new BalanceTrade(PARTICIPANT_A, PARTICIPANT_C, BigDecimal.valueOf(-123539000)),
                    new BalanceTrade(PARTICIPANT_A, PARTICIPANT_D, BigDecimal.valueOf(-25761000)),
                    new BalanceTrade(PARTICIPANT_B, PARTICIPANT_D, BigDecimal.valueOf(-21100000)),
                    new BalanceTrade(PARTICIPANT_A, PARTICIPANT_D, BigDecimal.valueOf(-100000))
                )
            )
        );

        when(eodCalculator.netAll(any())).thenReturn(
            Stream.of(ParticipantCurrencyPairAmount.of(PARTICIPANT_A, EURUSD, BigDecimal.TEN))
        );

        final JobExecution execution = jobLauncherTestUtils.launchStep(REBALANCE_POSITIONS_STEP_NAME,
            new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters())
                .addString(BUSINESS_DATE_JOB_PARAM_NAME, BUSINESS_DATE.format(BUSINESS_DATE_FMT))
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        verify(participantPositionRepository).findAllNetPositionsOfActiveLPByTradeDateFetchParticipant(BUSINESS_DATE);

        verify(eodCalculator).rebalanceLPPositions(positions, Map.of());

        verify(transactionsSender).send(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getTransactions())
            .extracting(
                NewTransaction::getBuyerParticipantId,
                NewTransaction::getSellerParticipantId,
                NewTransaction::getBaseCurrencyAmount,
                NewTransaction::getSpotRate,
                NewTransaction::getCurrencyPair,
                NewTransaction::getTradeDate
            )
            .containsExactlyInAnyOrder(
                transaction(PARTICIPANT_C, PARTICIPANT_A, 123539000),
                transaction(PARTICIPANT_D, PARTICIPANT_A, 25861000),
                transaction(PARTICIPANT_D, PARTICIPANT_B, 21100000)
            );

        verify(eodCalculator).netAll(netCaptor.capture());
        assertThat(netCaptor.getValue())
            .extracting(CcyParticipantAmount::getCurrencyPair, CcyParticipantAmount::getParticipant, CcyParticipantAmount::getAmount)
            .containsExactlyInAnyOrder(
                tuple(EURUSD, PARTICIPANT_A, BigDecimal.valueOf(-123539000)),
                tuple(EURUSD, PARTICIPANT_C, BigDecimal.valueOf(123539000)),
                tuple(EURUSD, PARTICIPANT_A, BigDecimal.valueOf(-25761000)),
                tuple(EURUSD, PARTICIPANT_D, BigDecimal.valueOf(25761000)),
                tuple(EURUSD, PARTICIPANT_B, BigDecimal.valueOf(-21100000)),
                tuple(EURUSD, PARTICIPANT_D, BigDecimal.valueOf(21100000)),
                tuple(EURUSD, PARTICIPANT_A, BigDecimal.valueOf(-100000)),
                tuple(EURUSD, PARTICIPANT_D, BigDecimal.valueOf(100000))
            );

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
            tuple(EURUSD, PARTICIPANT_A, BigDecimal.TEN, EURUSD_RATE, BUSINESS_DATE, VALUE_DATE, ParticipantPositionType.REBALANCING)
        );

        verify(tradeRepository).findAllBalanceByTradeDate(BUSINESS_DATE);
        verifyNoMoreInteractions(tradeRepository, eodCalculator, participantPositionRepository);

    }

    private static Tuple transaction(final ParticipantEntity buyer, final ParticipantEntity seller, final int amount) {
        return tuple(buyer.getCode(), seller.getCode(), Amount.of(BigDecimal.valueOf(amount), "EUR"), EURUSD_RATE,
            CurrencyPair.of(EURUSD.getBaseCurrency(), EURUSD.getValueCurrency()), BUSINESS_DATE);
    }

}