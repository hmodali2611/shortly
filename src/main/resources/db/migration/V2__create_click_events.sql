CREATE TABLE click_events (
    id          BIGSERIAL    PRIMARY KEY,
    short_code  VARCHAR(32)  NOT NULL REFERENCES links(short_code),
    occurred_at TIMESTAMPTZ  NOT NULL,
    referrer    VARCHAR(512),
    user_agent  VARCHAR(512),
    ip_hash     CHAR(64)
);

CREATE INDEX idx_clicks_code_time ON click_events (short_code, occurred_at DESC);