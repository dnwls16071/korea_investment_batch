package com.project.korea_investment_batch.kis.internal.processing.foreign;

import com.project.korea_investment_batch.kis.internal.processing.KisInstrumentType;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
enum ForeignSecurityCode {
    // KIS documents the security and ETP product codes in the overseas master column definitions.
    // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py#L33-L37
    EXCLUDED_1("1", null),
    STOCK("2", KisInstrumentType.STOCK),
    EXCHANGE_TRADED_PRODUCT("3", null),
    EXCLUDED_4("4", null);

    private static final Map<String, ForeignSecurityCode> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(code -> code.sourceCode, Function.identity()));
    private final String sourceCode;
    private final KisInstrumentType instrumentType;

    static KisInstrumentType classify(String securityCode, String productCode) {
        var code = BY_CODE.get(securityCode);
        if (code == null) {
            throw new IllegalArgumentException("Unrecognized US KIS security type");
        }
        if (code == EXCHANGE_TRADED_PRODUCT) {
            return switch (productCode) {
                case "001", "005" -> KisInstrumentType.ETF;
                case "002", "006" -> KisInstrumentType.ETN;
                case "000", "003", "004" -> null;
                default -> throw new IllegalArgumentException("Unrecognized US KIS product type");
            };
        }
        return code.instrumentType;
    }
}
