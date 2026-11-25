package com.project.korea_investment_batch.dart.internal.processing;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import com.project.korea_investment_batch.shared.CollectionRun;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class PublishDisclosuresTasklet {
    private final JdbcTemplate jdbc;

    public void publish(CollectionRun run, DartModule.Scan scan, CollectionCheckpoint checkpoint) {
        if ("COMPLETED".equals(scan.state())) {
            return;
        }
        if (scan.completeAttempt() == null) {
            throw new IllegalStateException("OpenDART scan has no validated page bundle");
        }
        jdbc.queryForObject("SELECT id FROM dart_publish_lock WHERE id = 1 FOR UPDATE", Integer.class);
        int inserted = jdbc.update("""
                INSERT INTO dart_disclosure (rcept_no, market, receipt_date, stock_code, corp_name, report_name)
                SELECT s.rcept_no, ?, ?, s.stock_code, s.corp_name, s.report_name
                FROM dart_stage s LEFT JOIN dart_disclosure d ON d.rcept_no = s.rcept_no
                WHERE s.scan_id = ? AND s.attempt = ? AND d.rcept_no IS NULL
                """, run.market().name(), run.date(), scan.id(), scan.completeAttempt());
        jdbc.update("""
                UPDATE dart_scan SET state = 'COMPLETED', new_count = ?, failure_step = NULL, failure_reason = NULL
                WHERE id = ?
                """, inserted, scan.id());
        checkpoint.reached("dart.publish.before-commit");
        CollectionCheckpoint.afterCommit(checkpoint, "dart.publish.after-commit");
    }
}
