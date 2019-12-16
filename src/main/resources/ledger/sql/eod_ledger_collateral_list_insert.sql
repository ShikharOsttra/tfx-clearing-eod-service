INSERT INTO eod_ledger_collateral_list(business_date,
                                       trade_date,
                                       evaluation_date,
                                       record_date,
                                       participant_code,
                                       participant_name,
                                       participant_type,
                                       collateral_purpose_type,
                                       collateral_purpose,
                                       collateral_name,
                                       collateral_type,
                                       security_code,
                                       isin_code,
                                       amount,
                                       market_price,
                                       evaluated_price,
                                       evaluated_amount,
                                       boj_code,
                                       jasdec_code,
                                       interest_payment_day,
                                       interest_payment_day2,
                                       maturity_date)
VALUES (:businessDate,
        :tradeDate,
        :evaluationDate,
        :recordDate,
        :participantCode,
        :participantName,
        :participantType,
        :collateralPurposeType,
        :collateralPurpose,
        :collateralName,
        :collateralType,
        :securityCode,
        :isinCode,
        :amount,
        :marketPrice,
        :evaluatedPrice,
        :evaluatedAmount,
        :bojCode,
        :jasdecCode,
        :interestPaymentDay,
        :interestPaymentDay2,
        :maturityDate);