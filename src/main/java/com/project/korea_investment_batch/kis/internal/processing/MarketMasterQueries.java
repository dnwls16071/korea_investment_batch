package com.project.korea_investment_batch.kis.internal.processing;

import com.project.korea_investment_batch.kis.KisModule.Change;
import com.project.korea_investment_batch.kis.KisModule.Input;
import com.project.korea_investment_batch.kis.KisModule.Instrument;
import com.project.korea_investment_batch.kis.KisModule.Snapshot;
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
public class MarketMasterQueries {

    private static final String LATEST = """
            SELECT
                s.id,
                i.collection_date,
                i.input_revision,
                m.last_collected_at AS collected_at,
                i.effective_date,
                i.instrument_count
            FROM kis_market_state m
            JOIN kis_snapshot s ON s.id = m.latest_snapshot_id
            JOIN kis_input i ON i.id = s.input_id WHERE m.market = ?
            """;
    private final JdbcClient jdbc;

    public Optional<Input> input(CollectionRun run) {
        return jdbc.sql("""
                SELECT
                    id,
                    archive_path,
                    archive_sha256,
                    parser_version,
                    collected_at,
                    effective_date,
                    instrument_count,
                    state,
                    failure_step,
                    failure_reason
                FROM kis_input
                WHERE market = ? AND collection_date = ? AND input_revision = ?
                """).params(run.market().name(), run.date(), run.revision()).query(Input.class).optional();
    }

    public void markFailed(CollectionRun run, String step, String reason) {
        long inputId = input(run).orElseThrow().id();
        jdbc.sql("""
                UPDATE kis_input SET state = 'FAILED', failure_step = ?, failure_reason = ?, instrument_count = ?
                WHERE id = ? AND state NOT IN ('PUBLISHED', 'SUPERSEDED')
                """).params(step, reason, stagedCount(inputId), inputId).update();
    }

    int stagedCount(long inputId) {
        return jdbc.sql("SELECT COUNT(*) FROM kis_stage WHERE input_id = ?").param(inputId).query(Integer.class).single();
    }

    public Optional<Input> lastAttempt(Market market) {
        return jdbc.sql("""
                SELECT
                    id,
                    archive_path,
                    archive_sha256,
                    parser_version,
                    collected_at,
                    effective_date,
                    instrument_count,
                    state,
                    failure_step,
                    failure_reason
                FROM kis_input
                WHERE market = ? ORDER BY collection_date DESC, input_revision DESC LIMIT 1
                """).param(market.name()).query(Input.class).optional();
    }

    public Optional<Snapshot> latest(Market market) {
        return jdbc.sql(LATEST).param(market.name()).query(Snapshot.class).optional();
    }

    Optional<Snapshot> latestForPublication(Market market) {
        return jdbc.sql(LATEST + " FOR UPDATE").param(market.name()).query(Snapshot.class).optional();
    }

    public List<Instrument> instruments(Market market) {
        return jdbc.sql("""
                SELECT
                    i.symbol,
                    i.standard_code,
                    i.name,
                    i.english_name,
                    i.instrument_type,
                    i.security_code,
                    i.product_code,
                    i.suspended
                FROM kis_market_state m
                JOIN kis_instrument i ON i.snapshot_id = m.latest_snapshot_id AND i.market = m.market
                WHERE m.market = ? ORDER BY i.symbol
                """).param(market.name()).query(Instrument.class).list();
    }

    public List<Change> changes(Market market, LocalDate date) {
        return jdbc.sql("""
                SELECT c.symbol, c.change_type, c.field, c.instrument_type, c.old_value, c.new_value,
                       i.collected_at, s.published_at
                FROM kis_change c JOIN kis_snapshot s ON s.id = c.published_snapshot_id
                JOIN kis_input i ON i.id = s.input_id
                WHERE c.market = ? AND i.collection_date = ?
                ORDER BY i.input_revision, c.symbol, c.change_type, c.field
                """).params(market.name(), date).query(Change.class).list();
    }
}
