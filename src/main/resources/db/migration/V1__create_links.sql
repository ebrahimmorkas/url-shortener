-- Starts high enough that every scrambled id encodes to exactly 7 Base62 characters.
CREATE SEQUENCE links_id_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE links
(
    id          BIGINT PRIMARY KEY,
    code        VARCHAR(30)   NOT NULL UNIQUE,
    target_url  VARCHAR(2048) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    expires_at  TIMESTAMPTZ,
    click_count BIGINT        NOT NULL DEFAULT 0
);
