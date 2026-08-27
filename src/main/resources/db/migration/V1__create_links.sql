CREATE TABLE links (
    short_code      VARCHAR(32)   PRIMARY KEY,
    target_url      VARCHAR(2048) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    expires_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    owner_key_id    VARCHAR(64)   NOT NULL,
    is_custom_alias BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_links_owner ON links (owner_key_id);
CREATE INDEX idx_links_expiry ON links (expires_at) WHERE expires_at IS NOT NULL;