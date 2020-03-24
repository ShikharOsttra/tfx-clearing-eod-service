package com.ihsmarkit.tfx.eod.batch.ledger.transactiondiary;

import static com.ihsmarkit.tfx.core.domain.type.CurrencyPairFamily.NON_NZD;
import static com.ihsmarkit.tfx.core.domain.type.ParticipantPositionType.NET;
import static com.ihsmarkit.tfx.core.domain.type.ParticipantPositionType.SOD;
import static com.ihsmarkit.tfx.core.domain.type.TradingHoursType.REGULAR;
import static com.ihsmarkit.tfx.core.domain.type.TradingHoursType.SUMMER;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.temporal.ChronoUnit.DAYS;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.ChronoLocalDate;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Range;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Component;

import com.ihsmarkit.tfx.core.dl.entity.SummerTimeSettingEntity;
import com.ihsmarkit.tfx.core.dl.entity.TradingHoursEntity;
import com.ihsmarkit.tfx.core.dl.repository.SummerTimeSettingRepository;
import com.ihsmarkit.tfx.core.dl.repository.TradingHoursRepository;
import com.ihsmarkit.tfx.core.domain.type.ParticipantPositionType;
import com.ihsmarkit.tfx.core.domain.type.TradingHoursType;

import lombok.RequiredArgsConstructor;

@Component
@StepScope
@RequiredArgsConstructor
public class PositionDateProvider {

    @Value("#{jobParameters['businessDate']}")
    private final LocalDate businessDate;

    private final SummerTimeSettingRepository summerTimeSettingRepository;

    private final TradingHoursRepository tradingHoursRepository;

    private Lazy<Map<ParticipantPositionType, LocalDateTime>> dates = Lazy.of(this::calculateDates);

    public LocalDateTime getSodDate() {
        return dates.get().get(SOD);
    }

    public LocalDateTime getNetDate() {
        return dates.get().get(NET);
    }

    private Map<ParticipantPositionType, LocalDateTime> calculateDates() {

        final SummerTimeSettingEntity summerTimeSetting = summerTimeSettingRepository.findByCurrencyPairFamilyFailFast(NON_NZD);
        final List<TradingHoursEntity> tradingHours = tradingHoursRepository.findAll();

        return Map.of(
            SOD, LocalDateTime.of(
                businessDate.with(saturdayIfMonday()),
                getCloseTime(businessDate.with(prevBusinessDay()), summerTimeSetting, tradingHours)
            ),
            NET, LocalDateTime.of(
                businessDate.plusDays(1),
                getCloseTime(businessDate, summerTimeSetting, tradingHours)
            ));
    }

    private static LocalTime getCloseTime(
        final LocalDate date,
        final SummerTimeSettingEntity summerTimeSetting,
        final List<TradingHoursEntity> tradingHours
    ) {
        final Range<ChronoLocalDate> summerTimeRange = Range.between(summerTimeSetting.getStartDate(), summerTimeSetting.getEndDate().minusDays(1));
        final TradingHoursType tradingHoursType = summerTimeRange.contains(date) ? SUMMER : REGULAR;
        return tradingHours.stream()
            .filter(tradingHour ->
                tradingHour.getType() == tradingHoursType &&
                    tradingHour.getDayOfWeek() == date.getDayOfWeek() &&
                    tradingHour.getCurrencyPairFamily() == NON_NZD
            )
            .map(TradingHoursEntity::getCloseTime)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Trading hours not found"));
    }

    private static TemporalAdjuster saturdayIfMonday() {
        return temporal -> DayOfWeek.from(temporal) == MONDAY ? temporal.with(TemporalAdjusters.previous(SATURDAY)) : temporal;
    }

    private static TemporalAdjuster prevBusinessDay() {
        return temporal -> isMonday(temporal) ? temporal.with(TemporalAdjusters.previous(FRIDAY)) : temporal.minus(1, DAYS);
    }

    private static boolean isMonday(final Temporal temporal) {
        return DayOfWeek.from(temporal) == MONDAY;
    }

}
