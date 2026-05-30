CREATE TABLE construction_queue (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL REFERENCES planet(id),
    building_type VARCHAR(32) NOT NULL,
    target_level INTEGER NOT NULL,
    metal_cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    crystal_cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    gas_cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completes_at TIMESTAMPTZ NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_construction_planet ON construction_queue(planet_id);
CREATE INDEX idx_construction_due ON construction_queue(completed, completes_at);
