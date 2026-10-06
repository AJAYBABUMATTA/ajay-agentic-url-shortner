CREATE TABLE short_urls (
    code VARCHAR(64) PRIMARY KEY,
    target VARCHAR(2048),
    management_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_clicks BIGINT NOT NULL DEFAULT 0 CHECK (total_clicks >= 0)
);
CREATE INDEX short_urls_expiry ON short_urls(expires_at);
CREATE TABLE url_daily_clicks (
    code VARCHAR(64) NOT NULL REFERENCES short_urls(code),
    utc_day DATE NOT NULL,
    clicks BIGINT NOT NULL CHECK (clicks >= 0),
    PRIMARY KEY(code,utc_day)
);
CREATE TABLE url_rate_buckets (
    identity_hash VARCHAR(64) NOT NULL,
    window_start BIGINT NOT NULL,
    used INTEGER NOT NULL CHECK (used > 0),
    PRIMARY KEY(identity_hash,window_start)
);
