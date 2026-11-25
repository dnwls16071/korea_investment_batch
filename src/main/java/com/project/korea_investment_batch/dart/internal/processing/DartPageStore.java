package com.project.korea_investment_batch.dart.internal.processing;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DartPageStore {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate pageTransaction;

    DartPageStore(
            JdbcTemplate jdbc,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.pageTransaction = new TransactionTemplate(transactionManager);
        pageTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int startAttempt(long scanId) {
        return pageTransaction.execute(transaction -> {
            int number = jdbc.queryForObject("SELECT COALESCE(MAX(attempt), 0) + 1 FROM dart_attempt WHERE scan_id = ?",
                    Integer.class, scanId);
            jdbc.update("INSERT INTO dart_attempt (scan_id, attempt) VALUES (?, ?)", scanId, number);
            jdbc.update("""
                    UPDATE dart_scan SET state = 'FETCHING', failure_step = NULL, failure_reason = NULL WHERE id = ?
                    """, scanId);
            return number;
        });
    }

    public void write(DisclosurePage.Validated validated) {
        var input = validated.page();
        var page = input.response();
        long scanId = input.scanId();
        int attempt = input.attempt();
        int number = input.number();
        pageTransaction.executeWithoutResult(transaction -> {
            boolean empty = "013".equals(page.status());
            jdbc.update("""
                    INSERT INTO dart_page (scan_id, attempt, page_no, total_pages, total_count, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, scanId, attempt, number, empty ? 0 : page.totalPages(), empty ? 0 : page.totalCount(), page.status());
            if (!empty) {
                List<Object[]> rows = page.receipts().stream().map(receipt -> new Object[]{scanId, attempt, number,
                        receipt.receiptNumber(), receipt.stockCode() == null ? "" : receipt.stockCode().strip(),
                        receipt.company(), receipt.title()}).toList();
                jdbc.batchUpdate("""
                        INSERT INTO dart_stage (scan_id, attempt, page_no, rcept_no, stock_code, corp_name, report_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, rows);
            }
            if (validated.lastPage()) {
                jdbc.update("""
                        UPDATE dart_scan SET complete_attempt = ?, scanned_at = ?, state = 'VALIDATED' WHERE id = ?
                        """, attempt, Timestamp.from(clock.instant()), scanId);
            }
        });
    }
}
