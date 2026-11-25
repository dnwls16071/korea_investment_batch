package com.project.korea_investment_batch.shared;

import java.time.LocalDate;
import java.util.Objects;

public record CollectionRun(Market market, LocalDate date, long revision) {

    public CollectionRun {
        Objects.requireNonNull(market);
        Objects.requireNonNull(date);
        if (revision < 1) {
            throw new IllegalArgumentException("Revision must be positive");
        }
    }
}
