package com.project.korea_investment_batch.kis.internal.processing;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class MarketMasterDiff {
    private static final String INSERT = """
            INSERT INTO kis_change
            (published_snapshot_id, market, symbol, change_type, field, instrument_type, old_value, new_value)
            """;
    private final JdbcTemplate jdbc;

    void record(long previous, long current, String market) {
        jdbc.update(INSERT + """
                SELECT n.snapshot_id, n.market, n.symbol,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM kis_instrument seen
                           WHERE seen.market = n.market AND seen.symbol = n.symbol AND seen.snapshot_id <> n.snapshot_id
                       ) THEN 'REAPPEARED' ELSE 'NEW' END,
                       'presence', n.instrument_type, 'ABSENT', 'PRESENT'
                FROM kis_instrument n LEFT JOIN kis_instrument p
                    ON p.snapshot_id = ? AND p.market = n.market AND p.symbol = n.symbol
                WHERE n.snapshot_id = ? AND n.market = ? AND p.symbol IS NULL
                """, previous, current, market);
        jdbc.update(INSERT + """
                SELECT ?, p.market, p.symbol, 'MISSING', 'presence', p.instrument_type, 'PRESENT', 'ABSENT'
                FROM kis_instrument p LEFT JOIN kis_instrument n
                    ON n.snapshot_id = ? AND n.market = p.market AND n.symbol = p.symbol
                WHERE p.snapshot_id = ? AND p.market = ? AND n.symbol IS NULL
                """, current, current, previous, market);
        recordField(previous, current, market, "name", "NAME_CHANGED", "n.name", "p.name");
        recordField(previous, current, market, "english_name", "NAME_CHANGED", "n.english_name", "p.english_name");
        recordField(previous, current, market, "suspended", "SUSPENSION_CHANGED",
                "IF(n.suspended IS NULL, NULL, IF(n.suspended, 'Y', 'N'))",
                "IF(p.suspended IS NULL, NULL, IF(p.suspended, 'Y', 'N'))");
    }

    private void recordField(long previous, long current, String market, String field, String type,
            String newValue, String oldValue) {
        jdbc.update(INSERT + """
                SELECT n.snapshot_id, n.market, n.symbol, ?, ?, n.instrument_type, %s, %s
                FROM kis_instrument n JOIN kis_instrument p
                    ON p.snapshot_id = ? AND p.market = n.market AND p.symbol = n.symbol
                WHERE n.snapshot_id = ? AND n.market = ? AND NOT (BINARY %s <=> BINARY %s)
                """.formatted(oldValue, newValue, oldValue, newValue), type, field, previous, current, market);
    }
}
