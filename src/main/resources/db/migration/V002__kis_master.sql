CREATE TABLE kis_input (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    market VARCHAR(10) NOT NULL,
    collection_date DATE NOT NULL,
    input_revision BIGINT NOT NULL,
    archive_path VARCHAR(1024),
    archive_sha256 CHAR(64),
    parser_version VARCHAR(30),
    collected_at TIMESTAMP(6),
    effective_date DATE,
    instrument_count INT,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_step VARCHAR(100),
    failure_reason VARCHAR(1000),
    UNIQUE KEY kis_input_identity (market, collection_date, input_revision)
) ENGINE=InnoDB;

CREATE TABLE kis_stage (
    input_id BIGINT NOT NULL,
    original_row_no BIGINT NOT NULL,
    symbol VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
    standard_code VARCHAR(12),
    name VARCHAR(300) NOT NULL,
    english_name VARCHAR(300),
    instrument_type VARCHAR(10) NOT NULL,
    security_code VARCHAR(10) NOT NULL,
    product_code VARCHAR(10) NOT NULL,
    suspended BOOLEAN,
    PRIMARY KEY (input_id, original_row_no),
    KEY kis_stage_symbol (input_id, symbol),
    FOREIGN KEY (input_id) REFERENCES kis_input (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE kis_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    input_id BIGINT NOT NULL UNIQUE,
    market VARCHAR(10) NOT NULL,
    published_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY kis_snapshot_market (id, market),
    FOREIGN KEY (input_id) REFERENCES kis_input (id)
) ENGINE=InnoDB;

CREATE TABLE kis_instrument (
    snapshot_id BIGINT NOT NULL,
    market VARCHAR(10) NOT NULL,
    symbol VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
    standard_code VARCHAR(12),
    name VARCHAR(300) NOT NULL,
    english_name VARCHAR(300),
    instrument_type VARCHAR(10) NOT NULL,
    security_code VARCHAR(10) NOT NULL,
    product_code VARCHAR(10) NOT NULL,
    suspended BOOLEAN,
    PRIMARY KEY (snapshot_id, market, symbol),
    KEY kis_instrument_market_symbol (market, symbol),
    FOREIGN KEY (snapshot_id, market) REFERENCES kis_snapshot (id, market)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE kis_market_state (
    market VARCHAR(10) PRIMARY KEY,
    latest_snapshot_id BIGINT,
    last_collected_at TIMESTAMP(6),
    FOREIGN KEY (latest_snapshot_id, market) REFERENCES kis_snapshot (id, market)
) ENGINE=InnoDB;

INSERT INTO kis_market_state (market) VALUES ('KOSPI'), ('KOSDAQ'), ('NASDAQ'), ('NYSE'), ('AMEX');
