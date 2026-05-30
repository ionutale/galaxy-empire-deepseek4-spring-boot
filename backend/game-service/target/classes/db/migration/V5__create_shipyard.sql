CREATE TABLE planet_ship (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    ship_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    UNIQUE (planet_id, ship_type)
);

CREATE TABLE shipyard_queue (
    id BIGSERIAL PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    ship_type VARCHAR(32) NOT NULL,
    quantity INT NOT NULL,
    built_quantity INT NOT NULL DEFAULT 0,
    metal_cost DOUBLE PRECISION NOT NULL,
    crystal_cost DOUBLE PRECISION NOT NULL,
    gas_cost DOUBLE PRECISION NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completes_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE
);
