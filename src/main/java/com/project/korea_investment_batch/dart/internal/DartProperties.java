package com.project.korea_investment_batch.dart.internal;

import java.time.Duration;

public record DartProperties(
        String apiKey,
        int pageSize,
        Duration timeout,
        int retries,
        Duration retryDelay,
        int lookbackDays
) {
    // OpenDART allows page_count values from 1 to 100.
    // https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001
    static final int MAX_PAGE_SIZE = 100;

    public DartProperties {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE || retries < 0 || lookbackDays < 0
                || timeout.isNegative() || timeout.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException("Invalid OpenDART collection settings");
        }
    }
}
