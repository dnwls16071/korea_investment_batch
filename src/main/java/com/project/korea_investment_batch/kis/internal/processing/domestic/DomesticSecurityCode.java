package com.project.korea_investment_batch.kis.internal.processing.domestic;

import com.project.korea_investment_batch.kis.internal.processing.KisInstrumentType;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
enum DomesticSecurityCode {
    // KIS defines the security-group field. The mappings below select the types this batch imports.
    // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/종목마스터정보(코스피).h#L9-L14
    ST(KisInstrumentType.STOCK),
    FS(KisInstrumentType.STOCK),
    DR(KisInstrumentType.STOCK),
    EF(KisInstrumentType.ETF),
    EN(KisInstrumentType.ETN),
    BC(null),
    RT(null),
    SW(null),
    PF(null),
    IF(null),
    MF(null),
    EW(null);

    private static final Map<String, DomesticSecurityCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(DomesticSecurityCode::name, Function.identity()));
    private final KisInstrumentType instrumentType;

    static KisInstrumentType classify(String securityCode) {
        var code = BY_CODE.get(securityCode);
        if (code == null) {
            throw new IllegalArgumentException("Unrecognized KIS domestic security type");
        }
        return code.instrumentType;
    }
}
