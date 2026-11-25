package com.project.korea_investment_batch.dart;

import com.project.korea_investment_batch.shared.CollectionExecution;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DartModule {
    void collect(LocalDate date);

    CollectionExecution scan(Market market, LocalDate date);

    CollectionExecution resume(CollectionRun run, boolean confirmedDead);

    Optional<CollectionExecution> execution(CollectionRun run);

    Optional<Scan> scanResult(CollectionRun run);

    Optional<Scan> latest(Market market, LocalDate date);

    List<Disclosure> disclosures(Market market, LocalDate date);

    record Scan(long id,
                long scanRevision,
                Integer completeAttempt,
                Instant scannedAt,
                Integer newCount,
                String state,
                String failureStep,
                String failureReason) {}

    record Disclosure(String receiptNumber,
                      Market market,
                      LocalDate receiptDate,
                      String stockCode,
                      String company,
                      String title) {
        public String originalLink() {
            // OpenDART documents the viewer URL with rcept_no passed as rcpNo.
            // https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001
            return "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receiptNumber;
        }
    }
}
