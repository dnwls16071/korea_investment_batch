package com.project.korea_investment_batch.kis.internal.processing;

import com.project.korea_investment_batch.shared.Market;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.project.korea_investment_batch.support.KisFixtures.american;
import static com.project.korea_investment_batch.support.KisFixtures.domestic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisMasterFormatsTest {
    @Test
    void readsTheDistinctDomesticLayouts() {
        var kospiParser = KisMasterFormats.forMarket(Market.KOSPI).parser();
        var kosdaqParser = KisMasterFormats.forMarket(Market.KOSDAQ).parser();
        var kospi = kospiParser.parse(domestic(Market.KOSPI, "000001", "한글 주식", "ST", ' ', 'Y'));
        var kosdaq = kosdaqParser.parse(domestic(Market.KOSDAQ, "000002", "한글 상장", "EN", '3', 'N'));
        assertThat(kospi.symbol()).isEqualTo("000001");
        assertThat(kospi.name()).isEqualTo("한글 주식");
        assertThat(kospi.standardCode()).isEqualTo("KR7000000001");
        assertThat(kospi.suspended()).isTrue();
        assertThat(kosdaq.type()).isEqualTo(KisInstrumentType.ETN);
        assertThat(kosdaq.productCode()).isEqualTo("3");
        assertThat(kosdaq.suspended()).isFalse();
        assertThat(kospiParser.parse(domestic(Market.KOSPI, "000003", "펀드", "EF", '2', 'N')).type())
                .isEqualTo(KisInstrumentType.ETF);
    }

    @Test
    void readsTwentyFourColumnsInEachAmericanMarketAndPreservesProductCodes() {
        for (var market : new Market[]{Market.AMEX, Market.NASDAQ, Market.NYSE}) {
            var parser = KisMasterFormats.forMarket(market).parser();
            assertThat(parser.parse(american(market, "AA.B", "3", "001")).productCode()).isEqualTo("001");
            assertThat(parser.parse(american(market, "AAA", "3", "002")).type()).isEqualTo(KisInstrumentType.ETN);
            assertThat(parser.parse(american(market, "AAA", "2", "")).type()).isEqualTo(KisInstrumentType.STOCK);
        }
        assertThatThrownBy(() -> KisMasterFormats.forMarket(Market.AMEX).parser().parse("broken"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsForeignRowsFromADifferentExchange() {
        var nyse = KisMasterFormats.forMarket(Market.NYSE).parser();
        assertThatThrownBy(() -> nyse.parse(american(Market.NASDAQ, "AAA", "2", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid US KIS exchange");
    }

    @Test
    void distinguishesExcludedDomesticGroupsFromUnknownGroups() {
        var parser = KisMasterFormats.forMarket(Market.KOSPI).parser();
        for (String code : List.of("ST", "FS", "DR")) {
            assertThat(parser.parse(domestic(Market.KOSPI, "000001", "종목", code, ' ', 'N')).type())
                    .isEqualTo(KisInstrumentType.STOCK);
        }
        for (String code : List.of("BC", "RT", "SW", "PF", "IF", "MF", "EW")) {
            var instrument = parser.parse(domestic(Market.KOSPI, "000002", "종목", code, ' ', 'N'));
            assertThat(instrument.type()).isNull();
            assertThat(instrument.securityCode()).isEqualTo(code);
        }
        assertThatThrownBy(() -> parser.parse(domestic(Market.KOSPI, "000003", "종목", "ZZ", ' ', 'N')))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized KIS domestic security type");
    }

    @Test
    void classifiesForeignProductsOnlyForTheExchangeTradedProductCode() {
        var parser = KisMasterFormats.forMarket(Market.NASDAQ).parser();
        assertThat(parser.parse(american(Market.NASDAQ, "AAA", "3", "005")).type()).isEqualTo(KisInstrumentType.ETF);
        assertThat(parser.parse(american(Market.NASDAQ, "AAA", "3", "006")).type()).isEqualTo(KisInstrumentType.ETN);
        for (String product : List.of("000", "003", "004")) {
            var instrument = parser.parse(american(Market.NASDAQ, "AAA", "3", product));
            assertThat(instrument.type()).isNull();
            assertThat(instrument.productCode()).isEqualTo(product);
        }
        for (String code : List.of("1", "4")) {
            assertThat(parser.parse(american(Market.NASDAQ, "AAA", code, "999")).type()).isNull();
        }
        assertThatThrownBy(() -> parser.parse(american(Market.NASDAQ, "AAA", "3", "999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized US KIS product type");
        assertThatThrownBy(() -> parser.parse(american(Market.NASDAQ, "AAA", "9", "001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized US KIS security type");
    }
}
