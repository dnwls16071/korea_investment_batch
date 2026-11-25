package com.project.korea_investment_batch.dart.internal.processing;

import com.project.korea_investment_batch.dart.DartModule.Disclosure;
import com.project.korea_investment_batch.dart.DartModule.Scan;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class DartDisclosureQueries {

    private final JdbcClient jdbc;

    public Optional<Scan> scan(CollectionRun run) {
        return jdbc.sql("""
                SELECT id, scan_revision, complete_attempt, scanned_at, new_count, state, failure_step, failure_reason
                FROM dart_scan WHERE market = ? AND receipt_date = ? AND scan_revision = ?
                """).params(run.market().name(), run.date(), run.revision()).query(Scan.class).optional();
    }

    public void markIncomplete(String market, LocalDate date, Long revision, String step, String reason) {
        jdbc.sql("""
                UPDATE dart_scan SET state = 'INCOMPLETE', failure_step = ?, failure_reason = ?
                WHERE market = ? AND receipt_date = ? AND scan_revision = ? AND state <> 'COMPLETED'
                """).params(step, reason, market, date, revision).update();
    }

    public Optional<Scan> latest(Market market, LocalDate date) {
        return jdbc.sql("""
                SELECT id, scan_revision, complete_attempt, scanned_at, new_count, state, failure_step, failure_reason
                FROM dart_scan WHERE market = ? AND receipt_date = ? ORDER BY scan_revision DESC LIMIT 1
                """).params(market.name(), date).query(Scan.class).optional();
    }

    public List<Disclosure> disclosures(Market market, LocalDate date) {
        return jdbc.sql("""
                SELECT rcept_no AS receipt_number, market, receipt_date, stock_code, corp_name AS company,
                       report_name AS title
                FROM dart_disclosure WHERE market = ? AND receipt_date = ? ORDER BY rcept_no
                """).params(market.name(), date).query(Disclosure.class).list();
    }
}
