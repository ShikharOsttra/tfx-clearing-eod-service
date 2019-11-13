package com.ihsmarkit.tfx.eod.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.ihsmarkit.tfx.core.dl.EntityTestDataFactory;
import com.ihsmarkit.tfx.core.dl.entity.AmountEntity;
import com.ihsmarkit.tfx.core.dl.entity.CurrencyPairEntity;
import com.ihsmarkit.tfx.core.dl.entity.LegalEntity;
import com.ihsmarkit.tfx.core.dl.entity.ParticipantEntity;
import com.ihsmarkit.tfx.core.dl.entity.TradeEntity;
import com.ihsmarkit.tfx.core.domain.type.Side;
import com.ihsmarkit.tfx.eod.model.BalanceTrade;

@ExtendWith(SpringExtension.class)
class BalanceTradeMapperTest {

    private static final CurrencyPairEntity CURRENCY_PAIR = EntityTestDataFactory.aCurrencyPairEntityBuilder().build();
    private static final LegalEntity ORIGINATOR_A = EntityTestDataFactory.aLegalEntityBuilder().name("A-LE").build();
    private static final LegalEntity ORIGINATOR_B = EntityTestDataFactory.aLegalEntityBuilder().name("B-LE").build();

    private static final ParticipantEntity PARTICIPANT_A = EntityTestDataFactory.aParticipantEntityBuilder()
        .name("A")
        .legalEntities(Collections.singletonList(ORIGINATOR_A))
        .build();

    private static final ParticipantEntity PARTICIPANT_B = EntityTestDataFactory.aParticipantEntityBuilder()
        .name("B")
        .legalEntities(Collections.singletonList(ORIGINATOR_B))
        .build();


    @Autowired
    private BalanceTradeMapper mapper;

    @Test
    void shouldMapToTrade() {
        TradeEntity tradeEntity = mapper.toTrade(
            new BalanceTrade(PARTICIPANT_A, PARTICIPANT_B, BigDecimal.TEN),
            LocalDate.of(2019, 11, 13),
            LocalDate.of(2019, 11, 15),
            CURRENCY_PAIR,
            BigDecimal.valueOf(2)
        );
        assertThat(tradeEntity.getBaseAmount()).isEqualTo(AmountEntity.of(BigDecimal.TEN, "USD"));
        assertThat(tradeEntity.getValueAmount()).isEqualTo(AmountEntity.of(BigDecimal.valueOf(5), "EUR"));
        assertThat(tradeEntity.getSpotRate()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(tradeEntity.getCounterparty()).isEqualTo(ORIGINATOR_B);
        assertThat(tradeEntity.getOriginator()).isEqualTo(ORIGINATOR_A);
        assertThat(tradeEntity.getCurrencyPair()).isEqualTo(CURRENCY_PAIR);
        assertThat(tradeEntity.getProductCode()).isEqualTo("USD/EUR");
        assertThat(tradeEntity.getDirection()).isEqualTo(Side.BUY);
        assertThat(tradeEntity.getTradeDate()).isEqualTo(LocalDate.of(2019, 11, 13));
        assertThat(tradeEntity.getValueDate()).isEqualTo(LocalDate.of(2019, 11, 15));
    }

    @Test
    void shouldMapToTradeForSale() {
        TradeEntity tradeEntity = mapper.toTrade(
            new BalanceTrade(PARTICIPANT_A, PARTICIPANT_B, BigDecimal.TEN.negate()),
            LocalDate.of(2019, 11, 13),
            LocalDate.of(2019, 11, 15),
            CURRENCY_PAIR,
            BigDecimal.valueOf(2)
        );

        assertThat(tradeEntity.getDirection()).isEqualTo(Side.SELL);

    }

    @TestConfiguration
    @ComponentScan(basePackageClasses = BalanceTradeMapper.class)
    static class TestConfig {

    }
}