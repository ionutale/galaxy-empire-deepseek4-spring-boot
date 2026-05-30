CREATE TABLE player_resource (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE,
    dark_matter INT NOT NULL DEFAULT 0
);
