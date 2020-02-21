package com.ihsmarkit.tfx.eod.service;

import java.time.LocalDate;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ihsmarkit.tfx.core.dl.entity.CurrencyPairEntity;
import com.ihsmarkit.tfx.core.dl.repository.calendar.CalendarTradingSwapPointRepository;
import com.ihsmarkit.tfx.eod.config.CacheConfig;

import lombok.RequiredArgsConstructor;

@Component
@JobScope
@RequiredArgsConstructor
public class TradeAndSettlementDateService {

    private final CalendarTradingSwapPointRepository calendarTradingSwapPointRepository;

    @Cacheable(value = CacheConfig.VALUE_DATES_CACHE, key = "T(com.ihsmarkit.tfx.eod.model.CurrencyPairKeyAndDate).of(#currencyPair, #tradeDate)")
    public LocalDate getValueDate(final LocalDate tradeDate, final CurrencyPairEntity currencyPair) {
        return calendarTradingSwapPointRepository.findValueDateByTradeDateAndCurrencyPairFailFast(tradeDate, currencyPair);
    }

    @Cacheable(value = CacheConfig.TRADE_DATES_CACHE, key = "T(com.ihsmarkit.tfx.eod.model.CurrencyPairKeyAndDate).of(#currencyPair, #tradeDate)")
    public LocalDate getNextTradeDate(final LocalDate tradeDate, final CurrencyPairEntity currencyPair) {
        return calendarTradingSwapPointRepository.findNextTradingDateFailFast(tradeDate, currencyPair);
    }

    @Cacheable(value = CacheConfig.VM_SETTLEMENT_DATES_CACHE, key = "T(com.ihsmarkit.tfx.eod.model.CurrencyPairKeyAndDate).of(#currencyPair, #tradeDate)")
    public LocalDate getVmSettlementDate(final LocalDate tradeDate, final CurrencyPairEntity currencyPair) {
        return calendarTradingSwapPointRepository.findVmSettlementDateByTradeDateAndCurrencyPairFailFast(tradeDate, currencyPair);
    }
}
