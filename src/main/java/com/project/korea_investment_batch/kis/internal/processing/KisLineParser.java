package com.project.korea_investment_batch.kis.internal.processing;

@FunctionalInterface
public interface KisLineParser {
    Instrument parse(String line);

    record Instrument(
            String symbol,
            String standardCode,
            String name,
            String englishName,
            KisInstrumentType type,
            String securityCode,
            String productCode,
            Boolean suspended
    ) {}
}
