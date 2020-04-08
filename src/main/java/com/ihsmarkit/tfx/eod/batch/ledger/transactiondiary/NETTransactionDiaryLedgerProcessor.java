package com.ihsmarkit.tfx.eod.batch.ledger.transactiondiary;

import static com.ihsmarkit.tfx.eod.batch.ledger.LedgerFormattingUtils.formatBigDecimal;
import static com.ihsmarkit.tfx.eod.batch.ledger.LedgerFormattingUtils.formatBigDecimalStripZero;
import static com.ihsmarkit.tfx.eod.batch.ledger.LedgerFormattingUtils.formatDate;
import static com.ihsmarkit.tfx.eod.batch.ledger.LedgerFormattingUtils.formatDateTime;
import static com.ihsmarkit.tfx.eod.batch.ledger.LedgerFormattingUtils.formatEnum;
import static com.ihsmarkit.tfx.eod.batch.ledger.LedgerFormattingUtils.formatTime;
import static org.apache.logging.log4j.util.Strings.EMPTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ihsmarkit.tfx.core.dl.entity.CurrencyPairEntity;
import com.ihsmarkit.tfx.core.dl.entity.ParticipantEntity;
import com.ihsmarkit.tfx.core.dl.entity.eod.ParticipantPositionEntity;
import com.ihsmarkit.tfx.core.dl.repository.eod.ParticipantPositionRepository;
import com.ihsmarkit.tfx.core.domain.type.ParticipantPositionType;
import com.ihsmarkit.tfx.eod.model.ParticipantAndCurrencyPair;
import com.ihsmarkit.tfx.eod.model.ledger.TransactionDiary;
import com.ihsmarkit.tfx.eod.service.DailySettlementPriceService;
import com.ihsmarkit.tfx.eod.service.FXSpotProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@StepScope
@Slf4j
public class NETTransactionDiaryLedgerProcessor implements TransactionDiaryLedgerProcessor<ParticipantAndCurrencyPair> {

    private static final char ORDER_ID_SUFFIX = '9';

    @Value("#{jobParameters['businessDate']}")
    private final LocalDate businessDate;

    @Value("#{stepExecutionContext['recordDate']}")
    private final LocalDateTime recordDate;

    private final DailySettlementPriceService dailySettlementPriceService;
    private final FXSpotProductService fxSpotProductService;

    private final ParticipantPositionRepository participantPositionRepository;
    private final TransactionDiaryOrderIdProvider transactionDiaryOrderIdProvider;
    private final PositionDateProvider positionDateProvider;

    @Override
    public TransactionDiary process(final ParticipantAndCurrencyPair participantAndCurrencyPair) {

        final ParticipantEntity participant = participantAndCurrencyPair.getParticipant();
        final CurrencyPairEntity currencyPair = participantAndCurrencyPair.getCurrencyPair();
        final int priceScale = fxSpotProductService.getScaleForCurrencyPair(currencyPair);
        final String productNumber = fxSpotProductService.getFxSpotProduct(currencyPair).getProductNumber();
        final LocalDateTime positionDateTime = positionDateProvider.getNetDate();

        return TransactionDiary.builder()
            .businessDate(businessDate)
            .tradeDate(formatDate(businessDate))
            .recordDate(formatDateTime(recordDate))
            .participantCode(participant.getCode())
            .participantName(participant.getName())
            .participantType(formatEnum(participant.getType()))
            .currencyNo(productNumber)
            .currencyPair(currencyPair.getCode())
            .matchDate(formatDate(positionDateTime.toLocalDate()))
            .matchTime(formatTime(positionDateTime))
            .matchId(EMPTY)
            .clearDate(formatDate(positionDateTime.toLocalDate()))
            .clearTime(formatTime(positionDateTime))
            .clearingId(EMPTY)
            .tradePrice(formatBigDecimal(dailySettlementPriceService.getPrice(businessDate, currencyPair), priceScale))
            .sellAmount(EMPTY)
            .buyAmount(EMPTY)
            .counterpartyCode(EMPTY)
            .counterpartyType(EMPTY)
            .dsp(formatBigDecimal(dailySettlementPriceService.getPrice(businessDate, currencyPair), priceScale))
            .dailyMtMAmount(EMPTY)
            .swapPoint(EMPTY)
            .outstandingPositionAmount(formatBigDecimalStripZero(getSODNextDayAmount(participant, currencyPair)))
            .settlementDate(EMPTY)
            .tradeId(EMPTY)
            .reference(EMPTY)
            .userReference(EMPTY)
            .orderId(transactionDiaryOrderIdProvider.getOrderId(participant.getCode(), productNumber, ORDER_ID_SUFFIX))
            .build();
    }

    private BigDecimal getSODNextDayAmount(final ParticipantEntity participant, final CurrencyPairEntity currencyPair) {
        final Optional<ParticipantPositionEntity> nextDayPosition = participantPositionRepository.findNextDayPosition(participant, currencyPair,
            ParticipantPositionType.SOD, businessDate);
        return nextDayPosition.map(entity -> entity.getAmount().getValue()).orElse(BigDecimal.ZERO);
    }
}