package com.project.korea_investment_batch.kis.internal.processing;

record KisMasterFormat(String entryName, KisLineParser parser) {
    String zipFileName() {
        return entryName + ".zip";
    }
}
