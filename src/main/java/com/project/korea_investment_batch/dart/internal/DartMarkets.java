package com.project.korea_investment_batch.dart.internal;

import com.project.korea_investment_batch.shared.Market;
import java.util.List;

public final class DartMarkets {

    public static final List<Market> SUPPORTED = List.of(Market.KOSPI, Market.KOSDAQ);

    private DartMarkets() {}

    public static String companyClass(Market market) {
        // OpenDART corp_cls codes for the two markets this batch collects.
        // https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001
        return switch (market) {
            case KOSPI -> "Y";
            case KOSDAQ -> "K";
            default -> throw new IllegalArgumentException("OpenDART supports KOSPI and KOSDAQ only");
        };
    }
}
