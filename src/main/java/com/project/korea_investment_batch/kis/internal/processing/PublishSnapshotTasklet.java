package com.project.korea_investment_batch.kis.internal.processing;

import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import com.project.korea_investment_batch.shared.CollectionRun;
import java.sql.Timestamp;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class PublishSnapshotTasklet {
    private final JdbcTemplate jdbc;
    private final MarketMasterQueries queries;
    private final MarketMasterDiff diff;

    public void publish(CollectionRun run, KisModule.Input input, ValidateSnapshotTasklet validation, CollectionCheckpoint checkpoint) {
        String market = run.market().name();
        jdbc.queryForObject("SELECT market FROM kis_market_state WHERE market = ? FOR UPDATE", String.class, market);
        if ("PUBLISHED".equals(input.state()) || "SUPERSEDED".equals(input.state())) {
            return;
        }
        // Use a locking read: an earlier input lookup may have opened an older MySQL read view.
        var previous = queries.latestForPublication(run.market()).orElse(null);
        if (previous != null && (run.date().isBefore(previous.collectionDate())
                || run.date().equals(previous.collectionDate()) && run.revision() <= previous.inputRevision())) {
            jdbc.update("UPDATE kis_input SET state = 'SUPERSEDED', failure_step = NULL, failure_reason = NULL WHERE id = ?",
                    input.id());
            return;
        }
        validation.verifyRetention(input.instrumentCount(), previous);
        jdbc.update("INSERT INTO kis_snapshot (input_id, market) VALUES (?, ?)", input.id(), market);
        long snapshot = jdbc.queryForObject("SELECT id FROM kis_snapshot WHERE input_id = ?", Long.class, input.id());
        jdbc.update("""
                INSERT INTO kis_instrument (snapshot_id, market, symbol, standard_code, name, english_name,
                                            instrument_type, security_code, product_code, suspended)
                SELECT ?, ?, symbol, standard_code, name, english_name, instrument_type, security_code, product_code,
                       suspended FROM kis_stage WHERE input_id = ?
                """, snapshot, market, input.id());
        if (previous != null) {
            diff.record(previous.id(), snapshot, market);
        }
        jdbc.update("""
                UPDATE kis_market_state SET latest_snapshot_id = ?, last_collected_at = ? WHERE market = ?
                """, snapshot, Timestamp.from(input.collectedAt()), market);
        jdbc.update("UPDATE kis_input SET state = 'PUBLISHED', failure_step = NULL, failure_reason = NULL WHERE id = ?",
                input.id());
        checkpoint.reached("kis.publish.before-commit");
        CollectionCheckpoint.afterCommit(checkpoint, "kis.publish.after-commit");
    }
}
