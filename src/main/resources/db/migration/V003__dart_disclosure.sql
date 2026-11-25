CREATE TABLE dart_scan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    market VARCHAR(10) NOT NULL,
    receipt_date DATE NOT NULL,
    scan_revision BIGINT NOT NULL,
    complete_attempt INT,
    scanned_at TIMESTAMP(6),
    new_count INT,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_step VARCHAR(100),
    failure_reason VARCHAR(1000),
    UNIQUE KEY dart_scan_identity (market, receipt_date, scan_revision)
) ENGINE=InnoDB;

CREATE TABLE dart_attempt (
    scan_id BIGINT NOT NULL,
    attempt INT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (scan_id, attempt),
    FOREIGN KEY (scan_id) REFERENCES dart_scan (id)
) ENGINE=InnoDB;

CREATE TABLE dart_page (
    scan_id BIGINT NOT NULL,
    attempt INT NOT NULL,
    page_no INT NOT NULL,
    total_pages INT NOT NULL,
    total_count INT NOT NULL,
    status CHAR(3) NOT NULL,
    PRIMARY KEY (scan_id, attempt, page_no),
    FOREIGN KEY (scan_id, attempt) REFERENCES dart_attempt (scan_id, attempt)
) ENGINE=InnoDB;

CREATE TABLE dart_stage (
    scan_id BIGINT NOT NULL,
    attempt INT NOT NULL,
    page_no INT NOT NULL,
    rcept_no CHAR(14) NOT NULL,
    stock_code VARCHAR(6) NOT NULL,
    corp_name VARCHAR(300) NOT NULL,
    report_name VARCHAR(500) NOT NULL,
    PRIMARY KEY (scan_id, attempt, rcept_no),
    FOREIGN KEY (scan_id, attempt, page_no) REFERENCES dart_page (scan_id, attempt, page_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dart_disclosure (
    rcept_no CHAR(14) PRIMARY KEY,
    market VARCHAR(10) NOT NULL,
    receipt_date DATE NOT NULL,
    stock_code VARCHAR(6) NOT NULL,
    corp_name VARCHAR(300) NOT NULL,
    report_name VARCHAR(500) NOT NULL,
    KEY dart_disclosure_date (market, receipt_date, rcept_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dart_publish_lock (
    id INT PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO dart_publish_lock (id) VALUES (1);
