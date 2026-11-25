package com.project.korea_investment_batch.kis.internal.processing;

import com.project.korea_investment_batch.kis.internal.KisProperties;
import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.CollectionRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ValidateSnapshotTasklet {
    private final JdbcTemplate jdbc;
    private final MarketMasterQueries queries;
    private final double minimumRetainedRatio;

    ValidateSnapshotTasklet(
            JdbcTemplate jdbc,
            MarketMasterQueries queries,
            KisProperties properties
    ) {
        this.jdbc = jdbc;
        this.queries = queries;
        this.minimumRetainedRatio = properties.minimumRetainedRatio();
    }

    public void validate(CollectionRun run, long inputId) {
        int count = queries.stagedCount(inputId);
        if (count == 0) {
            throw new IllegalArgumentException("KIS file has no supported instruments");
        }
        int unique = jdbc.queryForObject("SELECT COUNT(DISTINCT symbol) FROM kis_stage WHERE input_id = ?", Integer.class, inputId);
        if (count != unique) {
            throw new IllegalArgumentException("Duplicate KIS symbol in archived rows");
        }
        // A late input is still validated, but publication handles its ordering under the market lock.
        queries.latest(run.market()).filter(previous ->
                run.date().isAfter(previous.collectionDate()) || (run.date().equals(previous.collectionDate())
                        && run.revision() > previous.inputRevision()))
                .ifPresent(previous -> verifyRetention(count, previous));
        jdbc.update("""
                UPDATE kis_input SET state = 'VALIDATED', instrument_count = ?, failure_step = NULL,
                                     failure_reason = NULL WHERE id = ?
                """, count, inputId);
    }

    void verifyRetention(int count, KisModule.Snapshot previous) {
        if (previous != null && count < previous.instrumentCount() * minimumRetainedRatio) {
            throw new IllegalArgumentException("KIS instrument count fell from " + previous.instrumentCount() + " to " + count);
        }
    }
}
