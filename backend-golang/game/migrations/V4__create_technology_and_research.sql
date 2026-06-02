CREATE TABLE player_technology (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    technology VARCHAR(32) NOT NULL,
    level INT NOT NULL DEFAULT 0,
    UNIQUE (player_id, technology)
);

CREATE TABLE research_queue (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    technology VARCHAR(32) NOT NULL,
    target_level INT NOT NULL,
    metal_cost DOUBLE PRECISION NOT NULL,
    crystal_cost DOUBLE PRECISION NOT NULL,
    gas_cost DOUBLE PRECISION NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completes_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE
);
