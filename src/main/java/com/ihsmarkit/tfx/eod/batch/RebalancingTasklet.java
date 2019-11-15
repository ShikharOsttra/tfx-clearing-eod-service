package com.ihsmarkit.tfx.eod.batch;

import static com.ihsmarkit.tfx.eod.config.EodJobConstants.BUSINESS_DATE_FMT;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ihsmarkit.tfx.core.dl.entity.CurrencyPairEntity;
import com.ihsmarkit.tfx.core.dl.entity.TradeEntity;
import com.ihsmarkit.tfx.core.dl.entity.eod.ParticipantPositionEntity;
import com.ihsmarkit.tfx.core.dl.repository.TradeRepository;
import com.ihsmarkit.tfx.core.dl.repository.eod.ParticipantPositionRepository;
import com.ihsmarkit.tfx.core.domain.type.ParticipantPositionType;
import com.ihsmarkit.tfx.eod.mapper.BalanceTradeMapper;
import com.ihsmarkit.tfx.eod.mapper.ParticipantPositionForPairMapper;
import com.ihsmarkit.tfx.eod.model.BalanceTrade;
import com.ihsmarkit.tfx.eod.model.ParticipantPositionForPair;
import com.ihsmarkit.tfx.eod.service.DailySettlementPriceProvider;
import com.ihsmarkit.tfx.eod.service.EODCalculator;
import com.ihsmarkit.tfx.eod.service.SettlementDateProvider;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@JobScope
public class RebalancingTasklet implements Tasklet {


    private final ParticipantPositionRepository participantPositionRepository;

    private final TradeRepository tradeRepositiory;

    private final DailySettlementPriceProvider dailySettlementPriceProvider;

    private final EODCalculator eodCalculator;

    private final SettlementDateProvider settlementDateProvider;

    private final BalanceTradeMapper balanceTradeMapper;

    private final ParticipantPositionForPairMapper participantPositionForPairMapper;

    @Value("#{jobParameters['businessDate']}")
    private final String businessDateStr;

    @Override
    public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) throws Exception {
        final LocalDate businessDate = LocalDate.parse(businessDateStr, BUSINESS_DATE_FMT);
        final LocalDate settlementDate = settlementDateProvider.getSettlementDateFor(businessDate);

        final Map<CurrencyPairEntity, BigDecimal> dsp = dailySettlementPriceProvider.getDailySettlementPrices(businessDate);


        final Collection<ParticipantPositionEntity> positions =
            participantPositionRepository.findAllByPositionTypeAndTradeDateFetchCurrencyPair(ParticipantPositionType.NET, businessDate);

        final Map<@NonNull CurrencyPairEntity, List<BalanceTrade>> balanceTrades = eodCalculator.rebalanceLPPositions(positions);

        final Stream<TradeEntity> trades = balanceTrades.entrySet().stream()
            .flatMap(
                tradesByCcy -> tradesByCcy.getValue().stream()
                    .map(trade -> balanceTradeMapper.toTrade(
                        trade,
                        businessDate,
                        settlementDate,
                        tradesByCcy.getKey(),
                        dsp.get(tradesByCcy.getKey())
                    )
                )
            );

        final Stream<ParticipantPositionEntity> rebalanceNetPositions = eodCalculator.netAllTtrades(
            balanceTrades.entrySet().stream()
                .flatMap(
                    tradesByCcy -> tradesByCcy.getValue().stream()
                        .flatMap(
                            trade -> Stream.of(
                                ParticipantPositionForPair.of(trade.getOriginator(), tradesByCcy.getKey(), trade.getAmount()),
                                ParticipantPositionForPair.of(trade.getCounterpart(), tradesByCcy.getKey(), trade.getAmount().negate())
                            )
                        )
                )

        ).map(trade -> participantPositionForPairMapper.toParticipantPosition(
            trade,
            ParticipantPositionType.REBALANCING,
            businessDate,
            settlementDate,
            dsp.get(trade.getCurrencyPair())
        ));

        trades.collect(Collectors.toList());

        participantPositionRepository.saveAll(rebalanceNetPositions::iterator);

        return RepeatStatus.FINISHED;
    }
}
