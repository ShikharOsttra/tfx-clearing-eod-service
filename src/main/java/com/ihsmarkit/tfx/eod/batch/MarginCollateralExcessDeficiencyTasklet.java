package com.ihsmarkit.tfx.eod.batch;

import static com.ihsmarkit.tfx.eod.config.EodJobConstants.MARGIN_COLLATERAL_EXCESS_OR_DEFICIENCY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ihsmarkit.tfx.core.dl.entity.ParticipantEntity;
import com.ihsmarkit.tfx.core.dl.entity.eod.EodCashSettlementEntity;
import com.ihsmarkit.tfx.core.dl.entity.eod.EodProductCashSettlementEntity;
import com.ihsmarkit.tfx.core.dl.repository.collateral.CollateralBalanceRepository;
import com.ihsmarkit.tfx.core.dl.repository.eod.EodCashSettlementRepository;
import com.ihsmarkit.tfx.core.dl.repository.eod.EodParticipantMarginRepository;
import com.ihsmarkit.tfx.core.dl.repository.eod.EodProductCashSettlementRepository;
import com.ihsmarkit.tfx.core.dl.repository.eod.ParticipantPositionRepository;
import com.ihsmarkit.tfx.core.domain.type.CollateralPurpose;
import com.ihsmarkit.tfx.core.time.ClockService;
import com.ihsmarkit.tfx.eod.batch.ledger.collaterallist.CollateralCalculator;
import com.ihsmarkit.tfx.eod.mapper.CashSettlementMapper;
import com.ihsmarkit.tfx.eod.mapper.ParticipantMarginMapper;
import com.ihsmarkit.tfx.eod.model.BalanceContribution;
import com.ihsmarkit.tfx.eod.model.CashSettlement;
import com.ihsmarkit.tfx.eod.model.DayAndTotalCashSettlement;
import com.ihsmarkit.tfx.eod.service.EODCalculator;
import com.ihsmarkit.tfx.eod.service.JPYRateService;
import com.ihsmarkit.tfx.eod.service.MarginRatioService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@JobScope
@Slf4j
public class MarginCollateralExcessDeficiencyTasklet implements Tasklet {

    private final EodProductCashSettlementRepository eodProductCashSettlementRepository;

    private final ParticipantPositionRepository participantPositionRepository;

    private final EodCashSettlementRepository eodCashSettlementRepository;

    private final EodParticipantMarginRepository eodParticipantMarginRepository;

    private final CollateralBalanceRepository collateralBalanceRepository;

    private final ClockService clockService;

    private final JPYRateService jpyRateService;

    private final MarginRatioService marginRatioService;

    private final EODCalculator eodCalculator;

    private final CollateralCalculator collateralCalculator;

    private final ParticipantMarginMapper participantMarginMapper;

    private final CashSettlementMapper cashSettlementMapper;

    @Value("#{jobParameters['businessDate']}")
    private final LocalDate businessDate;

    @Override
    public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) {

        final List<EodProductCashSettlementEntity> margin =
            eodProductCashSettlementRepository.findAllBySettlementDateIsGreaterThanEqual(businessDate).collect(Collectors.toList());

        final var aggregated = eodCalculator.aggregateRequiredMargin(margin, businessDate);

        final Stream<EodCashSettlementEntity> cashSettlement = aggregated.entrySet().stream()
            .flatMap(
                byParticipant -> byParticipant.getValue().entrySet().stream()
                    .flatMap(byType -> byType.getValue().entrySet().stream()
                        .map(
                            byDateType -> CashSettlement.builder()
                                .participant(byParticipant.getKey())
                                .type(byType.getKey())
                                .dateType(byDateType.getKey())
                                .amount(byDateType.getValue())
                                .build()
                        ).map(settlement -> cashSettlementMapper.toEntity(settlement, businessDate, clockService.getCurrentDateTimeUTC()))
                    )
            );

        eodCashSettlementRepository.saveAll(cashSettlement::iterator);

        final var dayCashSettlement = eodCalculator.aggregateDayAndTotalCashSettlement(aggregated);

        final var requiredInitialMargin = eodCalculator
            .calculateRequiredInitialMargin(
                participantPositionRepository.findAllNetAndRebalancingPositionsByTradeDate(businessDate),
                marginRatioService::getRequiredMarginRatio,
                ccy -> jpyRateService.getJpyRate(businessDate, ccy)
            );

        final var deposits = calculateDeposits(dayCashSettlement, requiredInitialMargin);

        final var participantMargin =
            eodCalculator
                .calculateParticipantMargin(requiredInitialMargin, dayCashSettlement, deposits)
                .map(marginEntry -> participantMarginMapper.toEntity(marginEntry, businessDate, clockService.getCurrentDateTimeUTC()));

        eodParticipantMarginRepository.saveAll(participantMargin::iterator);

        return RepeatStatus.FINISHED;
    }

    private Map<ParticipantEntity, BalanceContribution> calculateDeposits(final Map<ParticipantEntity, DayAndTotalCashSettlement> dayCashSettlement,
                                                                          final Map<ParticipantEntity, BigDecimal> requiredInitialMargin) {
        final Set<Long> participants = uniqueParticipantIds(requiredInitialMargin, dayCashSettlement);
        if (participants.isEmpty()) {
            log.warn("[{}] no participants with collateral requirements found", MARGIN_COLLATERAL_EXCESS_OR_DEFICIENCY);
            return Map.of();
        } else {
            return eodCalculator.calculateDeposits(
                collateralBalanceRepository.findByParticipantIdAndPurpose(participants, Set.of(CollateralPurpose.MARGIN)).stream(),
                collateralCalculator::calculateEvaluatedAmount
            );
        }
    }

    private Set<Long> uniqueParticipantIds(
        final Map<ParticipantEntity, BigDecimal> requiredInitialMargin,
        final Map<ParticipantEntity, DayAndTotalCashSettlement> dayCashSettlement
    ) {
        return Stream.of(requiredInitialMargin, dayCashSettlement)
            .map(Map::keySet)
            .flatMap(Set::stream)
            .map(ParticipantEntity::getId)
            .collect(Collectors.toSet());
    }
}
