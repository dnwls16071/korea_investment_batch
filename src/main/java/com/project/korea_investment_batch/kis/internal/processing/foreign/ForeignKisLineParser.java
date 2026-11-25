package com.project.korea_investment_batch.kis.internal.processing.foreign;

import com.project.korea_investment_batch.kis.internal.processing.KisLineParser;

import static org.springframework.util.Assert.isTrue;

public record ForeignKisLineParser(String exchangeCode) implements KisLineParser {
    // KIS lists the overseas master columns in file order.
    // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py#L33-L37
    @Override
    public Instrument parse(String line) {
        String[] columns = line.split("\t", -1);
        isTrue(columns.length == 24, "Expected 24 columns in US KIS record");
        isTrue(columns[0].strip().equals("US") && columns[2].strip().equals(exchangeCode), "Invalid US KIS exchange");
        String symbol = columns[4].strip();
        String name = columns[6].strip();
        String englishName = columns[7].strip();
        String securityCode = columns[8].strip();
        String productCode = columns[22].strip();
        isTrue(symbol.matches("[A-Za-z0-9][A-Za-z0-9./-]{0,31}") && (!name.isEmpty() || !englishName.isEmpty()),
                "Invalid US KIS symbol or name");
        var type = ForeignSecurityCode.classify(securityCode, productCode);
        return new Instrument(symbol, null, name.isEmpty() ? englishName : name, englishName,
                type, securityCode, productCode, null);
    }
}
