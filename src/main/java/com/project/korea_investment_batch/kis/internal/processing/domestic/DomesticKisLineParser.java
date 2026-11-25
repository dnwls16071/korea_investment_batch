package com.project.korea_investment_batch.kis.internal.processing.domestic;

import com.project.korea_investment_batch.kis.internal.processing.KisLineParser;

import static org.springframework.util.Assert.isTrue;

public record DomesticKisLineParser(int suffixWidth, int productOffset, int haltOffset) implements KisLineParser {

    // KIS slices the decoded prefix before the variable-width Korean name.
    // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kospi_code_mst.py#L35-L43
    // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kosdaq_code_mst.py#L35-L43
    private static final int SHORT_CODE_WIDTH = 9;
    private static final int STANDARD_CODE_WIDTH = 12;
    private static final int NAME_OFFSET = SHORT_CODE_WIDTH + STANDARD_CODE_WIDTH;

    @Override
    public Instrument parse(String line) {
        int suffixStart = line.length() - suffixWidth;
        isTrue(suffixStart > NAME_OFFSET, "Invalid domestic KIS record width");
        String symbol = line.substring(0, SHORT_CODE_WIDTH).strip();
        String standardCode = line.substring(SHORT_CODE_WIDTH, NAME_OFFSET).strip();
        String name = line.substring(NAME_OFFSET, suffixStart).strip();
        String securityCode = line.substring(suffixStart, suffixStart + 2);
        String productCode = line.substring(suffixStart + productOffset, suffixStart + productOffset + 1).strip();
        char suspended = line.charAt(suffixStart + haltOffset);
        isTrue(symbol.matches("[A-Z0-9]{6,9}") && standardCode.matches("[A-Z0-9]{12}")
                && !name.isEmpty(), "Invalid domestic KIS code or name");
        isTrue(suspended == 'Y' || suspended == 'N', "Invalid KIS trading suspension flag");
        var type = DomesticSecurityCode.classify(securityCode);
        return new Instrument(symbol, standardCode, name, null, type, securityCode, productCode, suspended == 'Y');
    }
}
