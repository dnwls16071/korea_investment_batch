package com.project.korea_investment_batch.kis.internal.processing;

import com.project.korea_investment_batch.kis.internal.processing.domestic.DomesticKisLineParser;
import com.project.korea_investment_batch.kis.internal.processing.foreign.ForeignKisLineParser;
import com.project.korea_investment_batch.shared.Market;
import java.util.List;
import java.util.Map;

public final class KisMasterFormats {
    static final String PARSER_VERSION = "kis-cp949-v1";
    private static final Map<Market, KisMasterFormat> FORMATS = Map.of(
            // Width sums omit the line terminator. ETP and halt offsets sum the preceding field widths.
            // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kospi_code_mst.py#L35-L79
            // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kosdaq_code_mst.py#L35-L79
            Market.KOSPI, new KisMasterFormat("kospi_code.mst", new DomesticKisLineParser(227, 22, 60)),
            Market.KOSDAQ, new KisMasterFormat("kosdaq_code.mst", new DomesticKisLineParser(221, 18, 55)),
            // https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py#L24-L53
            Market.NASDAQ, new KisMasterFormat("nasmst.cod", new ForeignKisLineParser("NAS")),
            Market.NYSE, new KisMasterFormat("nysmst.cod", new ForeignKisLineParser("NYS")),
            Market.AMEX, new KisMasterFormat("amsmst.cod", new ForeignKisLineParser("AMS"))
    );
    private static final List<Market> SUPPORTED_MARKETS = FORMATS.keySet().stream().sorted().toList();

    private KisMasterFormats() {}

    public static List<Market> supportedMarkets() {
        return SUPPORTED_MARKETS;
    }

    static KisMasterFormat forMarket(Market market) {
        var format = FORMATS.get(market);
        if (format == null) {
            throw new IllegalArgumentException("Unsupported KIS market: " + market);
        }
        return format;
    }
}
