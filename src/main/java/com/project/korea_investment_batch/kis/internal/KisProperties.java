package com.project.korea_investment_batch.kis.internal;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public record KisProperties(
        Path archiveRoot,
        double minimumRetainedRatio,
        int chunkSize,
        Duration timeout,
        URI archiveBaseUrl
) {
    public KisProperties {
        if (!(minimumRetainedRatio > 0 && minimumRetainedRatio <= 1) || chunkSize < 1
                || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Invalid KIS collection settings");
        }
    }
}
