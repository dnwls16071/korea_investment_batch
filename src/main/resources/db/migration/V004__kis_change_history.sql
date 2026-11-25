CREATE TABLE kis_change (
    published_snapshot_id BIGINT NOT NULL,
    market VARCHAR(10) NOT NULL,
    symbol VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
    change_type VARCHAR(24) NOT NULL,
    field VARCHAR(20) NOT NULL,
    instrument_type VARCHAR(10) NOT NULL,
    old_value VARCHAR(300),
    new_value VARCHAR(300),
    PRIMARY KEY (published_snapshot_id, market, symbol, change_type, field),
    FOREIGN KEY (published_snapshot_id, market) REFERENCES kis_snapshot (id, market)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
